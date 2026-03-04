package com.suncorp.securehub.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.AiContextDto;
import com.suncorp.securehub.dto.AiDraftResponseDto;
import com.suncorp.securehub.dto.AiSuggestTagsResponseDto;
import com.suncorp.securehub.dto.AiSummarizeResponseDto;
import com.suncorp.securehub.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "bedrock")
public class BedrockAiAssistProvider implements AiAssistProvider {

    private final String modelId;
    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BedrockAiAssistProvider(
            @Value("${app.ai.bedrock.model-id:anthropic.claude-sonnet-4-6}") String modelId,
            @Value("${app.ai.bedrock.aws-region:ap-southeast-2}") String regionStr) {
        this.modelId = modelId;
        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(regionStr))
                .build();
    }

    @Override
    public AiSummarizeResponseDto summarize(AiContextDto context) {
        String prompt = "Please summarize the following support request. Respond only with the summary text, do not include any other markdown or pleasantries. If the request content is in Chinese, respond in Chinese; otherwise default to English.\n\n"
                + buildXmlContext(context);

        if (context.getUserPrompt() != null && !context.getUserPrompt().isEmpty()) {
            prompt += "\nUser extra instructions: " + context.getUserPrompt();
        }

        long start = System.currentTimeMillis();
        String response = callConverse(prompt, context);
        long latency = System.currentTimeMillis() - start;

        return AiSummarizeResponseDto.builder()
                .summary(response)
                .runId(UUID.randomUUID().toString())
                .provider(getProviderName())
                .model(getModelId())
                .latencyMs(latency)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public AiSuggestTagsResponseDto suggestTags(AiContextDto context) {
        // Request strict JSON output from the model so we can parse reliably
        String prompt = "Suggest up to 3 short category tags for the following support request.\n"
                + "Respond ONLY with valid JSON in this exact format, no other text:\n"
                + "{\"tags\":[{\"name\":\"tag-name\",\"reason\":\"brief reason\"},{\"name\":\"tag2\",\"reason\":\"reason\"}]}\n"
                + "Rules: tag names must be lowercase, 1-5 words, hyphen-separated where appropriate. "
                + "If the request is in Chinese, use Chinese tag names.\n\n"
                + buildXmlContext(context);

        if (context.getUserPrompt() != null && !context.getUserPrompt().isEmpty()) {
            prompt += "\nUser extra instructions: " + context.getUserPrompt();
        }

        long start = System.currentTimeMillis();
        String rawResponse = callConverse(prompt, context);
        long latency = System.currentTimeMillis() - start;

        // Provider returns raw name+reason only; AiAssistService will reconcile with
        // dictionary
        List<AiSuggestTagsResponseDto.TagSuggestion> tags = parseTagsFromResponse(rawResponse);

        return AiSuggestTagsResponseDto.builder()
                .tags(tags)
                .runId(UUID.randomUUID().toString())
                .provider(getProviderName())
                .model(getModelId())
                .latencyMs(latency)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * Parse model output into TagSuggestion list.
     * Strategy 1: Try to parse as JSON {"tags":[{"name":...,"reason":...},...]}
     * Strategy 2: Fall back to comma-split for backward compatibility
     * isNew / existingTagId are NOT set here — reconciliation happens in
     * AiAssistService.
     */
    private List<AiSuggestTagsResponseDto.TagSuggestion> parseTagsFromResponse(String raw) {
        List<AiSuggestTagsResponseDto.TagSuggestion> tags = new ArrayList<>();
        if (raw == null || raw.isBlank())
            return tags;

        // Strategy 1: JSON parse
        try {
            // Strip markdown code fences if present
            String cleaned = raw.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
            }
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode tagsNode = root.get("tags");
            if (tagsNode != null && tagsNode.isArray()) {
                for (JsonNode tagNode : tagsNode) {
                    String name = tagNode.has("name") ? tagNode.get("name").asText("").trim() : null;
                    String reason = tagNode.has("reason") ? tagNode.get("reason").asText("Suggested by AI")
                            : "Suggested by AI";
                    if (name != null && !name.isEmpty()) {
                        tags.add(AiSuggestTagsResponseDto.TagSuggestion.builder()
                                .name(name)
                                .reason(reason)
                                .build());
                    }
                }
                if (!tags.isEmpty())
                    return tags;
            }
        } catch (Exception e) {
            log.debug("Bedrock response was not JSON, falling back to comma-split: {}", e.getMessage());
        }

        // Strategy 2: comma-split fallback
        for (String part : raw.split(",")) {
            String name = part.trim().toLowerCase();
            if (!name.isEmpty()) {
                tags.add(AiSuggestTagsResponseDto.TagSuggestion.builder()
                        .name(name)
                        .reason("Suggested by AI")
                        .build());
            }
        }
        return tags;
    }

    @Override
    public AiDraftResponseDto draftResponse(AiContextDto context) {
        String prompt = "Please draft a helpful support response to the following support request. Address the user directly and be polite. If the request content is in Chinese, write the response in Chinese. Do NOT provide translation notes, just the drafted response.\n\n"
                + buildXmlContext(context);

        if (context.getUserPrompt() != null && !context.getUserPrompt().isEmpty()) {
            prompt += "\nUser extra instructions: " + context.getUserPrompt();
        }

        long start = System.currentTimeMillis();
        String response = callConverse(prompt, context);
        long latency = System.currentTimeMillis() - start;

        return AiDraftResponseDto.builder()
                .draft(response)
                .runId(UUID.randomUUID().toString())
                .provider(getProviderName())
                .model(getModelId())
                .latencyMs(latency)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public String getProviderName() {
        return "bedrock";
    }

    @Override
    public String getModelId() {
        return this.modelId;
    }

    private String buildXmlContext(AiContextDto context) {
        StringBuilder sb = new StringBuilder();
        sb.append("<request_title>").append(context.getRequestTitle()).append("</request_title>\n");
        sb.append("<request_description>").append(context.getRequestDescription()).append("</request_description>\n");

        if (context.getComments() != null && !context.getComments().isEmpty()) {
            sb.append("<comments>\n");
            for (AiContextDto.CommentContext c : context.getComments()) {
                sb.append("  <comment author=\"").append(c.getAuthor()).append("\" date=\"").append(c.getCreatedAt())
                        .append("\">")
                        .append(c.getContent())
                        .append("</comment>\n");
            }
            sb.append("</comments>\n");
        }

        if (context.getAttachments() != null && !context.getAttachments().isEmpty()) {
            sb.append("<attachments_metadata>\n");
            for (AiContextDto.AttachmentContext att : context.getAttachments()) {
                if (att.isIncluded() && att.getTextContent() != null && !att.getTextContent().isEmpty()) {
                    sb.append("  <attachment filename=\"").append(att.getFileName()).append("\">\n")
                            .append(att.getTextContent())
                            .append("\n  </attachment>\n");
                }
            }
            sb.append("</attachments_metadata>\n");
        }

        return sb.toString();
    }

    private String callConverse(String prompt, AiContextDto context) {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        // Add text prompt
        contentBlocks.add(ContentBlock.fromText(prompt));

        // Add multimodal attachments (images, documents)
        if (context.getAttachments() != null) {
            for (AiContextDto.AttachmentContext att : context.getAttachments()) {
                if (att.isIncluded() && att.getContentBytes() != null) {
                    try {
                        String mime = att.getContentType();
                        if (mime.startsWith("image/")) {
                            ImageFormat imgFmt = getImageFormat(mime);
                            if (imgFmt != null) {
                                contentBlocks.add(ContentBlock.fromImage(ImageBlock.builder()
                                        .format(imgFmt)
                                        .source(ImageSource.fromBytes(SdkBytes.fromByteArray(att.getContentBytes())))
                                        .build()));
                            }
                        } else if (mime.equals("application/pdf")) {
                            contentBlocks.add(ContentBlock.fromDocument(DocumentBlock.builder()
                                    .format(DocumentFormat.PDF)
                                    .name(sanitizeDocName(att.getFileName()))
                                    .source(DocumentSource.fromBytes(SdkBytes.fromByteArray(att.getContentBytes())))
                                    .build()));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to attach file {} to Bedrock converse API", att.getFileName(), e);
                    }
                }
            }
        }

        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(contentBlocks)
                .build();

        ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .messages(message)
                .build();

        try {
            ConverseResponse response = bedrockClient.converse(request);
            ContentBlock respBlock = response.output().message().content().get(0);
            return respBlock.text();
        } catch (BedrockRuntimeException e) {
            log.error("Bedrock API call failed: {}", e.getMessage(), e);
            throw new BadRequestException("AI Provider Error: " + e.getMessage());
        }
    }

    private ImageFormat getImageFormat(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> ImageFormat.JPEG;
            case "image/png" -> ImageFormat.PNG;
            case "image/webp" -> ImageFormat.WEBP;
            case "image/gif" -> ImageFormat.GIF;
            default -> null; // unsupported
        };
    }

    private String sanitizeDocName(String name) {
        if (name == null || name.isEmpty())
            return "document";
        // Bedrock rules: length <= 200, matches ^[a-zA-Z0-9\s\-\(\)\[\]_]+$
        String cleaned = name.replaceAll("[^a-zA-Z0-9\\s\\-\\(\\)\\[\\]_]", "_");
        if (cleaned.length() > 190) {
            cleaned = cleaned.substring(0, 190);
        }
        return cleaned;
    }
}
