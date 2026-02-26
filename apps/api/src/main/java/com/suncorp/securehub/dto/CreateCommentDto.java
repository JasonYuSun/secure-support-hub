package com.suncorp.securehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentDto {
    @NotBlank(message = "Comment body is required")
    private String body;
}
