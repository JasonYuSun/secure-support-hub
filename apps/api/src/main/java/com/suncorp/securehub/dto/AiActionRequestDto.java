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
    @Size(max = 2000)
    private String promptOverride; // optional extra instructions for the AI
}
