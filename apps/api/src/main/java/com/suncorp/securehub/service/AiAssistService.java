package com.suncorp.securehub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.entity.AiAssistRun;
import com.suncorp.securehub.repository.AiAssistRunRepository;
import com.suncorp.securehub.service.ai.AiAssistProvider;
import com.suncorp.securehub.service.ai.AiContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService {

    private final AiContextBuilder contextBuilder;
    private final AiAssistProvider provider;
    private final SupportRequestService supportRequestService;
    private final AiAssistRunRepository aiAssistRunRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiSummarizeResponseDto summarize(Long requestId, AiActionRequestDto reqDto, String username,
            Set<String> roles) {
        // Enforce RBAC by fetching the request first
        supportRequestService.getRequest(requestId, username, roles);
        AiContextDto context = contextBuilder.buildContext(requestId,
                reqDto != null ? reqDto.getPromptOverride() : null);

        AiSummarizeResponseDto response;
        try {
            response = provider.summarize(context);
            saveRun(requestId, "SUMMARIZE", context, response, "SUCCESS", null, null, response.getLatencyMs(), username,
                    response.getRunId());
        } catch (Exception e) {
            log.error("AI summarize failed", e);
            saveRun(requestId, "SUMMARIZE", context, null, "FAILED", "AI_PROVIDER_ERROR", e.getMessage(), 0L, username,
                    UUID.randomUUID().toString());
            throw new RuntimeException("AI summarize failed: " + e.getMessage(), e);
        }
        return response;
    }

    @Transactional
    public AiSuggestTagsResponseDto suggestTags(Long requestId, AiActionRequestDto reqDto, String username,
            Set<String> roles) {
        supportRequestService.getRequest(requestId, username, roles);
        AiContextDto context = contextBuilder.buildContext(requestId,
                reqDto != null ? reqDto.getPromptOverride() : null);

        AiSuggestTagsResponseDto response;
        try {
            response = provider.suggestTags(context);
            saveRun(requestId, "SUGGEST_TAGS", context, response, "SUCCESS", null, null, response.getLatencyMs(),
                    username, response.getRunId());
        } catch (Exception e) {
            log.error("AI suggest tags failed", e);
            saveRun(requestId, "SUGGEST_TAGS", context, null, "FAILED", "AI_PROVIDER_ERROR", e.getMessage(), 0L,
                    username, UUID.randomUUID().toString());
            throw new RuntimeException("AI suggest tags failed: " + e.getMessage(), e);
        }
        return response;
    }

    @Transactional
    public AiDraftResponseDto draftResponse(Long requestId, AiActionRequestDto reqDto, String username,
            Set<String> roles) {
        supportRequestService.getRequest(requestId, username, roles);
        AiContextDto context = contextBuilder.buildContext(requestId,
                reqDto != null ? reqDto.getPromptOverride() : null);

        AiDraftResponseDto response;
        try {
            response = provider.draftResponse(context);
            saveRun(requestId, "DRAFT_RESPONSE", context, response, "SUCCESS", null, null, response.getLatencyMs(),
                    username, response.getRunId());
        } catch (Exception e) {
            log.error("AI draft response failed", e);
            saveRun(requestId, "DRAFT_RESPONSE", context, null, "FAILED", "AI_PROVIDER_ERROR", e.getMessage(), 0L,
                    username, UUID.randomUUID().toString());
            throw new RuntimeException("AI draft response failed: " + e.getMessage(), e);
        }
        return response;
    }

    private void saveRun(Long requestId, String actionType, AiContextDto context, Object response,
            String status, String errorCode, String errorMessage, Long latencyMs,
            String username, String runIdStr) {
        UUID runId;
        try {
            runId = UUID.fromString(runIdStr);
        } catch (Exception e) {
            runId = UUID.randomUUID();
        }

        String inputJson = null;
        String outputJson = null;
        try {
            // Strip raw bytes from payload before saving
            if (context != null && context.getAttachments() != null) {
                context.getAttachments().forEach(att -> att.setContentBytes(null));
            }
            if (context != null)
                inputJson = objectMapper.writeValueAsString(context);
            if (response != null)
                outputJson = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AI payload", e);
        }

        AiAssistRun run = AiAssistRun.builder()
                .id(runId)
                .requestId(requestId)
                .actionType(actionType)
                .provider(provider.getProviderName())
                .modelId(provider.getModelId())
                .promptVersion("v1")
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
}
