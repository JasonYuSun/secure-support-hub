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
import java.util.Locale;
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
    private static final int MAX_BINARY_BYTES_PER_ATTACHMENT = 5 * 1024 * 1024;
    private static final int MAX_TOTAL_BINARY_BYTES_PER_REQUEST = 10 * 1024 * 1024;
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
        long[] totalBinaryBytes = { 0L };

        // Add request attachments
        List<Attachment> reqAttachments = attachmentRepository.findByRequest_IdOrderByCreatedAtAsc(requestId);
        for (Attachment att : reqAttachments) {
            attachmentContexts.add(buildAttachmentContext(att, totalBinaryBytes));
        }

        // Add comment attachments
        List<Attachment> commentAttachments = attachmentRepository
                .findByComment_Request_IdOrderByCreatedAtAsc(requestId);
        for (Attachment att : commentAttachments) {
            attachmentContexts.add(buildAttachmentContext(att, totalBinaryBytes));
        }

        return AiContextDto.builder()
                .requestTitle(request.getTitle())
                .requestDescription(request.getDescription())
                .comments(commentContexts)
                .attachments(attachmentContexts)
                .userPrompt(userPrompt)
                .build();
    }

    private AttachmentContext buildAttachmentContext(Attachment attachment, long[] totalBinaryBytes) {
        AttachmentContext context = AttachmentContext.builder()
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .included(false)
                .build();

        try {
            byte[] bytes = attachmentService.downloadAttachmentBytes(attachment.getId());
            if (!isDeclaredMimeConsistent(attachment.getContentType(), bytes)) {
                context.setSkipReason("Declared content type does not match file signature.");
                return context;
            }
            if (attachment.getContentType().startsWith("text/")
                    || attachment.getContentType().equals("application/csv")) {
                context.setTextContent(sanitizeTextAttachmentForAi(new String(bytes, StandardCharsets.UTF_8)));
                context.setIncluded(true);
            } else if (attachment.getContentType().equals("application/pdf")
                    || attachment.getContentType().startsWith("image/")) {
                if (bytes.length > MAX_BINARY_BYTES_PER_ATTACHMENT) {
                    context.setSkipReason("Attachment exceeds per-file binary AI context limit.");
                    return context;
                }
                if ((totalBinaryBytes[0] + bytes.length) > MAX_TOTAL_BINARY_BYTES_PER_REQUEST) {
                    context.setSkipReason("Attachment skipped: request-level binary AI context budget exceeded.");
                    return context;
                }
                context.setContentBytes(bytes);
                context.setIncluded(true);
                totalBinaryBytes[0] += bytes.length;
            } else {
                context.setSkipReason("Unsupported content type for AI context.");
            }
        } catch (Exception e) {
            log.warn("Failed to download bytes for attachment id {} in AI Context", attachment.getId(), e);
            context.setSkipReason("Failed to extract content from S3: " + e.getMessage());
        }

        return context;
    }

    static boolean isDeclaredMimeConsistent(String declaredContentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }

        String declared = normalizeMime(declaredContentType);
        return switch (declared) {
            case "application/pdf" -> hasPdfSignature(bytes);
            case "image/png" -> hasPngSignature(bytes);
            case "image/jpeg" -> hasJpegSignature(bytes);
            case "image/webp" -> hasWebpSignature(bytes);
            case "text/plain", "application/csv", "text/csv" -> isLikelyText(bytes);
            default -> false;
        };
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasPdfSignature(byte[] bytes) {
        return hasPrefix(bytes, new byte[] { '%', 'P', 'D', 'F', '-' });
    }

    private static boolean hasPngSignature(byte[] bytes) {
        return hasPrefix(bytes, new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
    }

    private static boolean hasJpegSignature(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean hasWebpSignature(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private static boolean hasPrefix(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLikelyText(byte[] bytes) {
        int sampleSize = Math.min(bytes.length, 2048);
        int suspiciousControlCount = 0;

        for (int i = 0; i < sampleSize; i++) {
            int b = bytes[i] & 0xFF;
            boolean allowed = b == 9 || b == 10 || b == 13 || (b >= 32 && b <= 126);
            if (!allowed) {
                suspiciousControlCount++;
            }
        }

        double suspiciousRatio = sampleSize == 0 ? 1.0 : (double) suspiciousControlCount / sampleSize;
        return suspiciousRatio <= 0.1;
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
