package com.suncorp.securehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContextDto {
    private String requestTitle;
    private String requestDescription;
    private List<CommentContext> comments;
    private List<AttachmentContext> attachments;
    private String userPrompt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentContext {
        private String author;
        private String content;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentContext {
        private String fileName;
        private String contentType;
        private byte[] contentBytes; // For multimodal models (PDFs/Images)
        private String textContent; // For text-based files
        private boolean included;
        private String skipReason;
    }
}
