package com.suncorp.securehub.service.ai;

import com.suncorp.securehub.dto.AiContextDto;
import com.suncorp.securehub.dto.AiContextDto.AttachmentContext;
import com.suncorp.securehub.dto.AiContextDto.CommentContext;
import com.suncorp.securehub.entity.Attachment;
import com.suncorp.securehub.entity.Comment;
import com.suncorp.securehub.entity.SupportRequest;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.AttachmentRepository;
import com.suncorp.securehub.repository.SupportRequestRepository;
import com.suncorp.securehub.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextBuilder {

    private final SupportRequestRepository requestRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentService attachmentService;
    private static final int CONTENT_MAX_CHARS_PER_ATTACHMENT = 10_000;
    private static final String FILTERED_INJECTION_TOKEN = "[FILTERED_INJECTION_PHRASE]";
    private static final String TRUNCATED_TOKEN = "[TRUNCATED_FOR_AI_CONTEXT]";
    private static final List<Pattern> ATTACHMENT_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(ignore|disregard|forget)\\b\\s+(all\\s+)?\\b(prior|previous|above)\\b\\s+\\binstructions?\\b"),
            Pattern.compile("(?i)\\boverride\\s+(all\\s+)?instructions?\\b"),
            Pattern.compile("(?i)\\byou\\s+are\\s+now\\b"),
            Pattern.compile("(?i)\\b(system|developer)\\s+(prompt|instructions?)\\b"));

    @Transactional(readOnly = true)
    public AiContextDto buildContext(Long requestId, String userPrompt) {
        SupportRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupportRequest", "id", requestId));

        List<CommentContext> commentContexts = new ArrayList<>();
        if (request.getComments() != null) {
            commentContexts = request.getComments().stream()
                    .sorted(Comparator.comparing(Comment::getCreatedAt))
                    .map(c -> CommentContext.builder()
                            .author(c.getAuthor().getUsername())
                            .content(c.getBody())
                            .createdAt(c.getCreatedAt().toString())
                            .build())
                    .collect(Collectors.toList());
        }

        List<AttachmentContext> attachmentContexts = new ArrayList<>();

        // Add request attachments
        List<Attachment> reqAttachments = attachmentRepository.findByRequest_IdOrderByCreatedAtAsc(requestId);
        for (Attachment att : reqAttachments) {
            attachmentContexts.add(buildAttachmentContext(att));
        }

        // Add comment attachments
        List<Attachment> commentAttachments = attachmentRepository
                .findByComment_Request_IdOrderByCreatedAtAsc(requestId);
        for (Attachment att : commentAttachments) {
            attachmentContexts.add(buildAttachmentContext(att));
        }

        return AiContextDto.builder()
                .requestTitle(request.getTitle())
                .requestDescription(request.getDescription())
                .comments(commentContexts)
                .attachments(attachmentContexts)
                .userPrompt(userPrompt)
                .build();
    }

    private AttachmentContext buildAttachmentContext(Attachment attachment) {
        AttachmentContext context = AttachmentContext.builder()
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .included(false)
                .build();

        try {
            byte[] bytes = attachmentService.downloadAttachmentBytes(attachment.getId());
            if (attachment.getContentType().startsWith("text/")
                    || attachment.getContentType().equals("application/csv")) {
                context.setTextContent(sanitizeTextAttachmentForAi(new String(bytes, StandardCharsets.UTF_8)));
                context.setIncluded(true);
            } else if (attachment.getContentType().equals("application/pdf")
                    || attachment.getContentType().startsWith("image/")) {
                context.setContentBytes(bytes);
                context.setIncluded(true);
            } else {
                context.setSkipReason("Unsupported content type for AI context.");
            }
        } catch (Exception e) {
            log.warn("Failed to download bytes for attachment id {} in AI Context", attachment.getId(), e);
            context.setSkipReason("Failed to extract content from S3: " + e.getMessage());
        }

        return context;
    }

    /**
     * Harden user-controlled text attachment content before it is included in AI
     * context.
     * Security controls:
     * 1) strip non-printable control chars (except CR/LF/TAB)
     * 2) redact common prompt-injection directives
     * 3) cap size to limit abuse and context exhaustion
     */
    static String sanitizeTextAttachmentForAi(String textContent) {
        if (textContent == null || textContent.isEmpty()) {
            return textContent;
        }

        String sanitized = textContent.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");

        for (Pattern pattern : ATTACHMENT_INJECTION_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(FILTERED_INJECTION_TOKEN);
        }

        if (sanitized.length() > CONTENT_MAX_CHARS_PER_ATTACHMENT) {
            sanitized = sanitized.substring(0, CONTENT_MAX_CHARS_PER_ATTACHMENT) + "\n" + TRUNCATED_TOKEN;
        }

        return sanitized;
    }
}
