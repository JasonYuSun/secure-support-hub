package com.suncorp.securehub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AI endpoint rate limiting (AI-004).
 *
 * <p>Defaults: 30 AI calls per hour per authenticated user.
 * Override via {@code AI_RATE_LIMIT_REQUESTS_PER_HOUR} environment variable.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.rate-limit")
public class RateLimitProperties {

    /**
     * Maximum number of AI requests allowed per user per hour.
     * Applies to all /api/v1/requests/{id}/ai/** endpoints.
     */
    private int requestsPerHour = 30;
}
