package com.suncorp.securehub.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Central sanitizer for all user-supplied AI hint text (promptOverride).
 *
 * <p>
 * This is the SINGLE source of truth for input normalization applied before
 * any user hint is included in AI prompts or audit records. All three AI
 * actions (summarize / suggest-tags / draft-response) MUST route hint text
 * through this component.
 *
 * <p>
 * Sanitization steps (applied in order):
 * <ol>
 * <li>Null-pass-through — null input returns null (no hint supplied).</li>
 * <li>Control-character stripping — removes ASCII 0x00–0x1F (except 0x20
 * space) and 0x7F DEL.</li>
 * <li>Whitespace normalization — collapses consecutive whitespace (spaces,
 * tabs, newlines) to a single space and trims leading/trailing whitespace.</li>
 * <li>Max-length enforcement — hard-truncates at
 * {@code app.ai.sanitizer.max-hint-length}
 * (default 500) on Unicode code-point boundaries so multibyte characters
 * are never split.</li>
 * <li>XML-significant character escaping — escapes {@code < > & " '} so that
 * sanitized output is safe to embed inside pseudo-XML context blocks
 * without breaking tag structure.</li>
 * </ol>
 *
 * <p>
 * {@link #xmlEscape(String)} is also available as a standalone utility for
 * escaping other user-controlled fields (e.g., request title, comment body,
 * attachment filename) before they are embedded in XML context.
 */
@Slf4j
@Component
public class AiInputSanitizer {

    private final int maxHintLength;

    public AiInputSanitizer(
            @Value("${app.ai.sanitizer.max-hint-length:500}") int maxHintLength) {
        if (maxHintLength <= 0) {
            throw new IllegalArgumentException("app.ai.sanitizer.max-hint-length must be > 0");
        }
        this.maxHintLength = maxHintLength;
    }

    /**
     * Sanitize a raw user hint string.
     *
     * @param rawHint the unvalidated user-supplied hint; may be null
     * @return sanitized hint ready for use in prompt assembly, or null if input was
     *         null
     */
    public String sanitize(String rawHint) {
        if (rawHint == null) {
            return null;
        }

        // Step 1: strip control characters (keep space 0x20; remove 0x00–0x1F and 0x7F)
        String stripped = stripControlChars(rawHint);

        // Step 2: normalize whitespace
        String normalized = stripped.replaceAll("\\s+", " ").strip();

        if (normalized.isEmpty()) {
            return null;
        }

        // Step 3: enforce max length (truncate on code-point boundary)
        String truncated = truncateOnCodePoint(normalized, maxHintLength);

        // Step 4: XML-escape so the hint cannot break out of its bounded context tag
        String escaped = xmlEscape(truncated);

        log.debug("AiInputSanitizer: rawLen={} sanitizedLen={}", rawHint.length(), escaped.length());
        return escaped;
    }

    /**
     * Escape XML-significant characters in {@code input}.
     * Safe to call on any user-controlled text before embedding in pseudo-XML
     * context.
     *
     * @param input raw string; null returns empty string
     * @return XML-escaped string
     */
    public static String xmlEscape(String input) {
        if (input == null) {
            return "";
        }
        // Order matters: replace & first to avoid double-encoding
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static String stripControlChars(String input) {
        // Remove chars in 0x00–0x1F range (except 0x09 tab, 0x0A LF, 0x0D CR which
        // the whitespace normalizer will collapse) and 0x7F DEL.
        // We keep tab/LF/CR so whitespace normalization can collapse them uniformly.
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') {
                sb.append(' '); // treat as whitespace; will be normalized
            } else if (c < 0x20 || c == 0x7F) {
                // strip — dangerous control chars
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String truncateOnCodePoint(String input, int maxLength) {
        // Truncate on Unicode code-point boundary (never splits surrogate pairs)
        int[] codePoints = input.codePoints().toArray();
        if (codePoints.length <= maxLength) {
            return input;
        }
        return new String(codePoints, 0, maxLength);
    }
}
