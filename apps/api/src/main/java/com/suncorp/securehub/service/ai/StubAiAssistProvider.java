package com.suncorp.securehub.service.ai;

import com.suncorp.securehub.dto.AiContextDto;
import com.suncorp.securehub.dto.AiDraftResponseDto;
import com.suncorp.securehub.dto.AiSuggestTagsResponseDto;
import com.suncorp.securehub.dto.AiSummarizeResponseDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Stub AI provider used in tests (and by default when no 'bedrock' config is
 * present).
 * Returns deterministic test data. isNew / existingTagId are NOT set here —
 * they are
 * populated by AiAssistService.reconcileWithDictionary() so tests exercise real
 * reconciliation logic.
 */
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubAiAssistProvider implements AiAssistProvider {

        @Override
        public AiSummarizeResponseDto summarize(AiContextDto context) {
                return AiSummarizeResponseDto.builder()
                                .summary("This is a stub summary for request: " + context.getRequestTitle()
                                                + "\nAttachments included: "
                                                + context.getAttachments().size())
                                .runId(UUID.randomUUID().toString())
                                .provider(getProviderName())
                                .model(getModelId())
                                .latencyMs(100L)
                                .generatedAt(OffsetDateTime.now())
                                .build();
        }

        @Override
        public AiSuggestTagsResponseDto suggestTags(AiContextDto context) {
                // Provider returns name+reason only; AiAssistService reconciles against
                // dictionary
                return AiSuggestTagsResponseDto.builder()
                                .tags(List.of(
                                                AiSuggestTagsResponseDto.TagSuggestion.builder()
                                                                .name("login-issue")
                                                                .reason("Mentions login")
                                                                .build(),
                                                AiSuggestTagsResponseDto.TagSuggestion.builder()
                                                                .name("urgent")
                                                                .reason("Stub reason urgent")
                                                                .build()))
                                .runId(UUID.randomUUID().toString())
                                .provider(getProviderName())
                                .model(getModelId())
                                .latencyMs(120L)
                                .generatedAt(OffsetDateTime.now())
                                .build();
        }

        @Override
        public AiDraftResponseDto draftResponse(AiContextDto context) {
                return AiDraftResponseDto.builder()
                                .draft("Hello, \n\nWe have received your request regarding '"
                                                + context.getRequestTitle()
                                                + "' and are looking into it.\n\nBest, Support Team")
                                .runId(UUID.randomUUID().toString())
                                .provider(getProviderName())
                                .model(getModelId())
                                .latencyMs(150L)
                                .generatedAt(OffsetDateTime.now())
                                .build();
        }

        @Override
        public String getProviderName() {
                return "stub";
        }

        @Override
        public String getModelId() {
                return "stub-model-id";
        }
}
