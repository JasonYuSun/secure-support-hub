package com.suncorp.securehub.service.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOutputSanitizerTest {

    private final AiOutputSanitizer sanitizer = new AiOutputSanitizer();

    @Test
    void sanitizeText_redactsInlineSecrets() {
        String raw = "jwt_secret=abc123 password: p@ssw0rd api_key=my-key token=tkn";
        String sanitized = sanitizer.sanitizeText(raw);

        assertThat(sanitized).contains("jwt_secret=[REDACTED]");
        assertThat(sanitized).contains("password: [REDACTED]");
        assertThat(sanitized).contains("api_key=[REDACTED]");
        assertThat(sanitized).contains("token=[REDACTED]");
        assertThat(sanitized).doesNotContain("abc123").doesNotContain("p@ssw0rd").doesNotContain("my-key");
    }

    @Test
    void sanitizeText_redactsPrivateKeyBlocks() {
        String raw = "before\n-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\nafter";
        String sanitized = sanitizer.sanitizeText(raw);

        assertThat(sanitized).contains("[REDACTED_PRIVATE_KEY]");
        assertThat(sanitized).doesNotContain("BEGIN PRIVATE KEY").doesNotContain("END PRIVATE KEY");
    }

    @Test
    void sanitizeText_stripsUnsafeControlChars() {
        String raw = "hello\u0000\u0001world";
        String sanitized = sanitizer.sanitizeText(raw);
        assertThat(sanitized).isEqualTo("helloworld");
    }

    @Test
    void sanitizeText_truncatesLongContent() {
        String raw = "A".repeat(8200);
        String sanitized = sanitizer.sanitizeText(raw);
        assertThat(sanitized.length()).isLessThanOrEqualTo(8022);
        assertThat(sanitized).contains("[TRUNCATED_AI_OUTPUT]");
    }

    @Test
    void sanitizeTagName_trimsAndCapsLength() {
        String raw = "   urgent   security   incident   ";
        String sanitized = sanitizer.sanitizeTagName(raw);
        assertThat(sanitized).isEqualTo("urgent security incident");
    }
}
