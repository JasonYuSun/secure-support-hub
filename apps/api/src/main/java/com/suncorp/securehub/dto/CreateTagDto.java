package com.suncorp.securehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTagDto {
    @NotBlank(message = "Tag name must not be blank")
    @Size(max = 100, message = "Tag name must not exceed 100 characters")
    private String name;
}
