package com.suncorp.securehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentUploadUrlResponseDto {
    private Long attachmentId;
    private String uploadUrl;
    private Instant expiresAt;
}
