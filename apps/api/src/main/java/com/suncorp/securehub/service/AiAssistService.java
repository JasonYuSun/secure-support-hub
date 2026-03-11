package com.suncorp.securehub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.entity.AiAssistRun;
import com.suncorp.securehub.entity.Tag;
import com.suncorp.securehub.repository.AiAssistRunRepository;
import com.suncorp.securehub.repository.TagRepository;
import com.suncorp.securehub.service.ai.AiAssistProvider;
import com.suncorp.securehub.service.ai.AiContextBuilder;
import com.suncorp.securehub.service.ai.AiInputSanitizer;
import com.suncorp.securehub.service.ai.AiOutputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService {

    private final AiContextBuilder contextBuilder;
    private final AiAssistProvider provider;
    private final SupportRequestService supportRequestService;
    private final AiAssistRunRepository aiAssistRunRepository;
    private final TagRepository tagRepository;
    private final ObjectMapper objectMapper;
    private final AiInputSanitizer aiInputSanitizer;
    private final AiOutputSanitizer aiOutputSanitizer;
    @Value("${app.ai.prompt-version:v1}")
    private String promptVersion;

    private static final int MAX_TAG_NAME_LENGTH = 100;

    @Transactional
    public AiSummarizeResponseDto summarize(Long requestId, AiActionRequestDto reqDto, String username,
            Set<String> roles) {
        // Enforce RBAC by fetching the request first
        supportRequestService.getRequest(requestId, username, roles);

        String rawHint = reqDto != null ? reqDto.getPromptOverride() : null;
        String sanitizedHint = aiInputSanitizer.sanitize(rawHint);

        AiContextDto context = contextBuilder.buildContext(requestId, sanitizedHint);

        AiSummarizeResponseDto response;
        try {
            response = provider.summarize(context);
            response = sanitizeSummarizeResponse(response);
            saveRun(requestId, "SUMMARIZE", context, response, "SUCCESS", null, null, response.getLatencyMs(), username,
                    response.getRunId(), rawHint);
        } catch (Exception e) {
            log.error("AI summarize failed", e);
            saveRun(requestId, "SUMMARIZE", context, null, "FAILED", "AI_PROVIDER_ERROR", e.getMessage(), 0L, username,
                    UUID.randomUUID().toString(), rawHint);
            throw new RuntimeException("AI summarize failed: " + e.getMessage(), e);
        }
        return response;
    }

    @Transactional
    public AiSuggestTagsResponseDto suggestTags(Long requestId, AiActionRequestDto reqDto, String username,
            Set<String> roles) {
        supportRequestService.getRequest(requestId, username, roles);

        String rawHint = reqDto != null ? reqDto.getPromptOverride() : null;
        String sanitizedHint = aiInputSanitizer.sanitize(rawHint);

        AiContextDto context = contextBuilder.buildContext(requestId, sanitizedHint);

        AiSuggestTagsResponseDto response;
        try {
            response = provider.suggestTags(context);
            // Post-process: reconcile provider output against the tag dictionary
            response = reconcileWithDictionary(response);
            response = sanitizeSuggestTagsResponse(response);
            saveRun(requestId, "SUGGEST_TAGS", context, response, "SUCCESS", null, null, response.getLatencyMs(),
                    username, response.getRunId(), rawHint);
        } catch (Exception e) {
            log.error("AI suggest tags failed", e);
            saveRun(requestId, "SUGGEST_TAGS", context, null, "FAILED", "AI_PROVIDER_ERROR", e.getMessage(), 0L,
                    username, UUID.randomUUID().toString(), rawHint);
            throw new RuntimeException("AI suggest tags failed: " + e.getMessage(), e);
        }
        return response;
    }

    /**
     * Post-process raw provider suggestions against the tag dictionary:
     * 1. Normalize each name (trim + collapse whitespace)
     * 2. Reject invalid names (blank or exceeds max length)
     * 3. Deduplicate by normalized name (first occurrence wins)
     * 4. Look up each name in the tag dictionary (case-insensitive, active only):
     * - Found → existingTagId = tag.id, isNew = false
     * - Not found → existingTagId = null, isNew = true
     */
    private AiSuggestTagsResponseDto reconcileWithDictionary(AiSuggestTagsResponseDto raw) {
        if (raw.getTags() == null || raw.getTags().isEmpty()) {
            return raw;
        }

        List<AiSuggestTagsResponseDto.TagSuggestion> reconciled = new ArrayList<>();
        Set<String> seenNormalized = new LinkedHashSet<>();

        for (AiSuggestTagsResponseDto.TagSuggestion suggestion : raw.getTags()) {
            if (suggestion.getName() == null)
                continue;

            // Normalize: trim + collapse internal whitespace
            String normalized = suggestion.getName().trim().replaceAll("\\s+", " ");

            // Reject blank or over-length names
            if (normalized.isEmpty() || normalized.length() > MAX_TAG_NAME_LENGTH) {
                log.debug("AI tag suggestion rejected (invalid name): '{}'", suggestion.getName());
                continue;
            }

            // Deduplicate by normalized name (case-insensitive key)
            String dedupeKey = normalized.toLowerCase(Locale.ROOT);
            if (seenNormalized.contains(dedupeKey)) {
                log.debug("AI tag suggestion deduplicated: '{}'", normalized);
                continue;
            }
            seenNormalized.add(dedupeKey);

            // Reconcile with dictionary
            Optional<Tag> existingTag = tagRepository.findByNameIgnoreCaseAndDeletedAtIsNull(normalized);

            AiSuggestTagsResponseDto.TagSuggestion built = AiSuggestTagsResponseDto.TagSuggestion.builder()
                    .name(normalized)
                    .reason(suggestion.getReason())
                    .existingTagId(existingTag.map(Tag::getId).orElse(null))
                    .isNew(existingTag.isEmpty())
                    .build();

            reconciled.add(built);
        }

        return AiSuggestTagsResponseDto.builder()
                .tags(reconciled)
                .runId(raw.getRunId())
                .provider(raw.getProvider())
                .model(raw.getModel())
                .latencyMs(raw.getLatencyMs())
                .generatedAt(raw.getGeneratedAt())
                .build();
    }

    @Transactional
    public AiDraftResponseDto draftResponse(Long requestId, AiActionRequestDto reqDto, String username,
            Set<String> roles) {
        supportRequestService.getRequest(requestId, username, roles);

        String rawHint = reqDto != null ? reqDto.getPromptOverride() : null;
        String sanitizedHint = aiInputSanitizer.sanitize(rawHint);

        AiContextDto context = contextBuilder.buildContext(requestId, sanitizedHint);

        AiDraftResponseDto response;
        try {
            response = provider.draftResponse(context);
            response = sanitizeDraftResponse(response);
            saveRun(requestId, "DRAFT_RESPONSE", context, response, "SUCCESS", null, null, response.getLatencyMs(),
                    username, response.getRunId(), rawHint);
        } catch (Exception e) {
            log.error("AI draft response failed", e);
            saveRun(requestId, "DRAFT_RESPONSE", context, null, "FAILED", "AI_PROVIDER_ERROR", e.getMessage(), 0L,
                    username, UUID.randomUUID().toString(), rawHint);
            throw new RuntimeException("AI draft response failed: " + e.getMessage(), e);
        }
        return response;
    }

    /**
     * Store a minimal audit record for an AI action run.
     *
     * <p>
     * <strong>Security note:</strong> This method deliberately does NOT persist raw
     * request text, comments, or attachment content. Doing so would create a
     * secondary
     * PII data-exposure risk (AI-002). Instead it stores:
     * <ul>
     * <li>Non-PII metadata (requestId, action, counts)</li>
     * <li>The sanitizedHintLength (length of the already-sanitized hint)</li>
     * <li>A SHA-256 hex digest of the raw hint for forensic traceability without
     * persisting its content</li>
     * <li>Output metadata only (lengths/counts), never raw model text</li>
     * </ul>
     *
     * @param rawHint the original (pre-sanitization) user hint — only its hash is
     *                persisted
     */
    private void saveRun(Long requestId, String actionType, AiContextDto context, Object response,
            String status, String errorCode, String errorMessage, Long latencyMs,
            String username, String runIdStr, String rawHint) {
        UUID runId;
        try {
            runId = UUID.fromString(runIdStr);
        } catch (Exception e) {
            runId = UUID.randomUUID();
        }

        String inputJson = null;
        String outputJson = null;
        try {
            // Build minimal metadata-only snapshot — no raw user content (AI-001/AI-002
            // fix)
            Map<String, Object> inputAudit = new LinkedHashMap<>();
            inputAudit.put("requestId", requestId);
            inputAudit.put("actionType", actionType);
            inputAudit.put("promptVersion", promptVersion);
            inputAudit.put("hasAttachments",
                    context != null && context.getAttachments() != null && !context.getAttachments().isEmpty());
            inputAudit.put("commentCount",
                    context != null && context.getComments() != null ? context.getComments().size() : 0);
            inputAudit.put("sanitizedHintLength",
                    context != null && context.getUserPrompt() != null ? context.getUserPrompt().length() : 0);
            // SHA-256 of the raw (pre-sanitization) hint for forensic traceability
            inputAudit.put("rawHintSha256", sha256Hex(rawHint));

            inputJson = objectMapper.writeValueAsString(inputAudit);

            outputJson = objectMapper.writeValueAsString(buildOutputAudit(actionType, response, runId.toString()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AI audit payload", e);
        }

        AiAssistRun run = AiAssistRun.builder()
                .id(runId)
                .requestId(requestId)
                .actionType(actionType)
                .provider(provider.getProviderName())
                .modelId(provider.getModelId())
                .promptVersion(promptVersion)
                .inputSnapshot(inputJson)
                .outputPayload(outputJson)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .latencyMs(latencyMs)
                .createdBy(username)
                .createdAt(OffsetDateTime.now())
                .build();

        aiAssistRunRepository.save(run);
    }

    /**
     * Build a metadata-only output audit snapshot.
     *
     * <p>
     * This intentionally excludes raw model output text (summary/draft) to avoid
     * storing potential PII or sensitive content in audit rows (AI-002 fix).
     */
    private Map<String, Object> buildOutputAudit(String actionType, Object response, String runId) {
        Map<String, Object> outputAudit = new LinkedHashMap<>();
        outputAudit.put("actionType", actionType);
        outputAudit.put("runId", runId);
        outputAudit.put("hasOutput", response != null);

        if (response == null) {
            return outputAudit;
        }

        if (response instanceof AiSummarizeResponseDto summarize) {
            outputAudit.put("summaryLength", summarize.getSummary() != null ? summarize.getSummary().length() : 0);
            outputAudit.put("provider", summarize.getProvider());
            outputAudit.put("model", summarize.getModel());
            outputAudit.put("latencyMs", summarize.getLatencyMs());
            outputAudit.put("generatedAt", summarize.getGeneratedAt());
            return outputAudit;
        }

        if (response instanceof AiDraftResponseDto draft) {
            outputAudit.put("draftLength", draft.getDraft() != null ? draft.getDraft().length() : 0);
            outputAudit.put("provider", draft.getProvider());
            outputAudit.put("model", draft.getModel());
            outputAudit.put("latencyMs", draft.getLatencyMs());
            outputAudit.put("generatedAt", draft.getGeneratedAt());
            return outputAudit;
        }

        if (response instanceof AiSuggestTagsResponseDto tags) {
            int tagCount = tags.getTags() != null ? tags.getTags().size() : 0;
            int newTagCount = tags.getTags() != null
                    ? (int) tags.getTags().stream().filter(AiSuggestTagsResponseDto.TagSuggestion::isNew).count()
                    : 0;
            outputAudit.put("tagCount", tagCount);
            outputAudit.put("newTagCount", newTagCount);
            outputAudit.put("provider", tags.getProvider());
            outputAudit.put("model", tags.getModel());
            outputAudit.put("latencyMs", tags.getLatencyMs());
            outputAudit.put("generatedAt", tags.getGeneratedAt());
            return outputAudit;
        }

        // Fallback for unexpected response types.
        outputAudit.put("responseType", response.getClass().getSimpleName());
        return outputAudit;
    }

    private AiSummarizeResponseDto sanitizeSummarizeResponse(AiSummarizeResponseDto response) {
        if (response == null) {
            return null;
        }
        response.setSummary(aiOutputSanitizer.sanitizeText(response.getSummary()));
        return response;
    }

    private AiDraftResponseDto sanitizeDraftResponse(AiDraftResponseDto response) {
        if (response == null) {
            return null;
        }
        response.setDraft(aiOutputSanitizer.sanitizeText(response.getDraft()));
        return response;
    }

    private AiSuggestTagsResponseDto sanitizeSuggestTagsResponse(AiSuggestTagsResponseDto response) {
        if (response == null || response.getTags() == null) {
            return response;
        }

        List<AiSuggestTagsResponseDto.TagSuggestion> sanitizedTags = response.getTags().stream()
                .map(tag -> AiSuggestTagsResponseDto.TagSuggestion.builder()
                        .existingTagId(tag.getExistingTagId())
                        .name(aiOutputSanitizer.sanitizeTagName(tag.getName()))
                        .reason(aiOutputSanitizer.sanitizeText(tag.getReason()))
                        .isNew(tag.isNew())
                        .build())
                .filter(tag -> tag.getName() != null && !tag.getName().isBlank())
                .toList();

        response.setTags(sanitizedTags);
        return response;
    }

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of a string, or null if
     * input is null.
     * Used for forensic traceability of raw user hints without persisting their
     * content.
     */
    private static String sha256Hex(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available — raw_hint_sha256 will be null", e);
            return null;
        }
    }
}
