package com.suncorp.securehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestTagsResponseDto {
    private List<TagSuggestion> tags;
    private String runId;
    private String provider;
    private String model;
    private Long latencyMs;
    private OffsetDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagSuggestion {
        private Long existingTagId; // if it matches an existing tag
        private String name;
        private boolean isNew;
        private String reason;
    }
}
