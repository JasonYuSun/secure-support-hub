package com.suncorp.securehub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.suncorp.securehub.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user rate-limiting filter for AI assist endpoints (AI-004 fix).
 *
 * <p>Intercepts all {@code /api/v1/requests/{id}/ai/} sub-paths and enforces a
 * per-authenticated-user token-bucket limit (default: 30 calls/hour).
 *
 * <p>On limit breach, returns HTTP 429 Too Many Requests with a
 * {@code Retry-After} header and a JSON error payload.
 *
 * <p>Buckets are stored in a Caffeine in-memory cache keyed by username.
 * Cache entries expire 2 hours after last access to reclaim memory for
 * inactive users.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AI_PATH_PREFIX = "/api/v1/requests/";
    private static final String AI_PATH_SEGMENT = "/ai/";

    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    /** Per-user bucket cache. Expire 2h after last access. */
    private final Cache<String, Bucket> buckets;

    /**
     * Optional test-only override for the per-hour limit.
     * When non-zero, this value is used instead of the configured property.
     * Set via {@link #setTestLimitOverride(int)} in integration tests.
     */
    private final AtomicInteger testLimitOverride = new AtomicInteger(0);

    public RateLimitFilter(RateLimitProperties rateLimitProperties, ObjectMapper objectMapper) {
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.HOURS)
                .maximumSize(10_000)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getServletPath() returns empty string in MockMvc; use getRequestURI() as fallback
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if (!path.startsWith(AI_PATH_PREFIX)) {
            return true;
        }
        int aiSegmentIdx = path.indexOf(AI_PATH_SEGMENT, AI_PATH_PREFIX.length());
        return aiSegmentIdx < 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = auth.getName();
        int limit = resolveLimit();
        Bucket bucket = buckets.get(username, u -> createBucket(limit));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("AI rate limit exceeded for user={} path={} limit={}",
                    username, request.getServletPath(), limit);
            long refillSeconds = Duration.ofHours(1).toSeconds();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(refillSeconds));
            Map<String, Object> body = Map.of(
                    "status", 429,
                    "error", "AI_RATE_LIMIT_EXCEEDED",
                    "message", "AI request rate limit exceeded. You are allowed "
                            + limit
                            + " AI requests per hour. Please retry after "
                            + refillSeconds + " seconds."
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
        }
    }

    private int resolveLimit() {
        int override = testLimitOverride.get();
        return override > 0 ? override : rateLimitProperties.getRequestsPerHour();
    }

    /**
     * Clears all per-user buckets.
     * Public for integration-test isolation — not part of production API.
     */
    public void clearBuckets() {
        buckets.invalidateAll();
    }

    /**
     * Overrides the rate limit for this filter instance.
     * For integration-test isolation ONLY — set to 0 to restore default behavior.
     * Call {@link #clearBuckets()} after setting the override to ensure all new
     * buckets are created with the overridden limit.
     */
    public void setTestLimitOverride(int limit) {
        testLimitOverride.set(limit);
    }

    private Bucket createBucket(int limit) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit)
                .refillGreedy(limit, Duration.ofHours(1))
                .build();
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }
}
