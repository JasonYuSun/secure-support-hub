package com.suncorp.securehub.service.ai;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class AiOutputSanitizer {

    private static final int MAX_OUTPUT_TEXT_CHARS = 8000;

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    private static final List<Pattern> SECRET_VALUE_PATTERNS = List.of(
            Pattern.compile("(?i)(jwt[_-]?secret\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(password\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(api[_-]?key\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(token\\s*[:=]\\s*)\\S+"));

    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "(?s)-----BEGIN[^\\n]*PRIVATE KEY-----.*?-----END[^\\n]*PRIVATE KEY-----");

    public String sanitizeText(String text) {
        if (text == null) {
            return null;
        }

        String sanitized = CONTROL_CHARS.matcher(text).replaceAll("");
        for (Pattern pattern : SECRET_VALUE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("$1[REDACTED]");
        }
        sanitized = PRIVATE_KEY_BLOCK.matcher(sanitized).replaceAll("[REDACTED_PRIVATE_KEY]");

        if (sanitized.length() > MAX_OUTPUT_TEXT_CHARS) {
            sanitized = sanitized.substring(0, MAX_OUTPUT_TEXT_CHARS) + "\n[TRUNCATED_AI_OUTPUT]";
        }

        return sanitized;
    }

    public String sanitizeTagName(String tagName) {
        String sanitized = sanitizeText(tagName);
        if (!StringUtils.hasText(sanitized)) {
            return null;
        }
        sanitized = sanitized.trim().replaceAll("\\s+", " ");
        return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
    }
}
