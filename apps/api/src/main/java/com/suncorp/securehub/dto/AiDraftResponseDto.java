package com.suncorp.securehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDraftResponseDto {
    private String draft;
    private String runId;
    private String provider;
    private String model;
    private Long latencyMs;
    private OffsetDateTime generatedAt;
}
