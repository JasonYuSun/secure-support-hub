package com.suncorp.securehub.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiActionRequestDto {
    @Size(max = 500, message = "promptOverride must not exceed 500 characters")
    private String promptOverride; // optional user context hint — sanitized before use
}
