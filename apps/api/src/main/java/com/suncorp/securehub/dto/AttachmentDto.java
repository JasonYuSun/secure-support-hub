package com.suncorp.securehub.dto;

import com.suncorp.securehub.entity.AttachmentState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDto {
    private Long id;
    private Long requestId;
    private Long commentId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private AttachmentState state;
    private UserDto uploadedBy;
    private LocalDateTime createdAt;
}
