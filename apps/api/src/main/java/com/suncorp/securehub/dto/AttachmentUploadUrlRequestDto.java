package com.suncorp.securehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AttachmentUploadUrlRequestDto {
    @NotBlank(message = "File name is required")
    private String fileName;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;
}
