package com.suncorp.securehub.dto;

import com.suncorp.securehub.entity.SupportRequest.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SupportRequestDto {
    private Long id;
    private String title;
    private String description;
    private RequestStatus status;
    private UserDto createdBy;
    private UserDto assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int commentCount;
}
