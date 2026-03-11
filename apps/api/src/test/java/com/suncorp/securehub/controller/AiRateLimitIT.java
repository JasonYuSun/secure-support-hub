package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.CreateRequestDto;
import com.suncorp.securehub.dto.LoginRequest;
import com.suncorp.securehub.filter.RateLimitFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AI-004: per-user rate limiting on AI assist endpoints.
 *
 * Uses {@link RateLimitFilter#setTestLimitOverride(int)} to set a low limit of 2
 * for fast test execution without requiring 30 calls.
 * Buckets are cleared before each test via {@link RateLimitFilter#clearBuckets()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AiRateLimitIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    RateLimitFilter rateLimitFilter;

    private String userToken;
    private String triageToken;
    private Long requestId;

    @BeforeEach
    void setUp() throws Exception {
        // Set a low limit (2/hr) directly on the filter for fast test execution
        rateLimitFilter.setTestLimitOverride(2);
        // Clear any stale buckets so each test gets fresh, limit=2 buckets
        rateLimitFilter.clearBuckets();

        userToken = login("user", "password");
        triageToken = login("triage", "password");
        requestId = createRequest(userToken, "Rate limit test request");
    }

    @AfterEach
    void tearDown() {
        // Restore default behavior and clear test buckets
        rateLimitFilter.setTestLimitOverride(0);
        rateLimitFilter.clearBuckets();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String login(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Long createRequest(String token, String title) throws Exception {
        CreateRequestDto dto = new CreateRequestDto();
        dto.setTitle(title + " " + System.currentTimeMillis());
        dto.setDescription("Rate limiting integration test description");
        MvcResult result = mockMvc.perform(post("/api/v1/requests")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── core 429 behaviour ────────────────────────────────────────────────────

    /**
     * With limit=2, calls 1 and 2 must succeed (200). Call 3 must return 429.
     */
    @Test
    void summarize_afterLimitExceeded_returns429() throws Exception {
        // Call 1 — should succeed
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        // Call 2 — should succeed (limit = 2)
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        // Call 3 — must be rate-limited
        MvcResult limitedResult = mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        // Verify Retry-After header is set
        String retryAfter = limitedResult.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isGreaterThan(0);

        // Verify JSON error payload
        String body = limitedResult.getResponse().getContentAsString();
        assertThat(body).contains("AI_RATE_LIMIT_EXCEEDED");
    }

    /**
     * Limit is per-user, not global — a different user's bucket must not be
     * affected by the first user exhausting theirs.
     */
    @Test
    void rateLimitIsPerUser_differentUserNotAffected() throws Exception {
        Long triageRequest = createRequest(triageToken, "Triage rate limit test");

        // Exhaust user's rate limit (2 calls)
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isOk());
        }

        // User is now rate-limited
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isTooManyRequests());

        // Triage user has an independent bucket — must still get 200
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", triageRequest)
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    /**
     * Different AI endpoint types still count against the same per-user bucket.
     */
    @Test
    void rateLimitCoversAllAiEndpoints() throws Exception {
        // Call 1 — summarize
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        // Call 2 — draft-response (different endpoint, same bucket)
        mockMvc.perform(post("/api/v1/requests/{id}/ai/draft-response", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        // Call 3 — suggest-tags — bucket exhausted, must be 429
        mockMvc.perform(post("/api/v1/requests/{id}/ai/suggest-tags", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Under the rate limit, calls still succeed normally.
     */
    @Test
    void underLimit_aiCallsSucceedNormally() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.runId").isNotEmpty());
    }
}
