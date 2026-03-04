package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.CreateRequestDto;
import com.suncorp.securehub.dto.CreateTagDto;
import com.suncorp.securehub.dto.LoginRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the AI suggest-tags flow, exercising dictionary
 * reconciliation,
 * deduplication, and multi-tag apply idempotency.
 * Uses the Stub provider (default) so no real Bedrock calls are made.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AiSuggestTagsIT {

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

    private String userToken;
    private String triageToken;

    @BeforeEach
    void setUp() throws Exception {
        userToken = login("user", "password");
        triageToken = login("triage", "password");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private Long createRequest(String token) throws Exception {
        CreateRequestDto dto = new CreateRequestDto();
        dto.setTitle("AI tag test " + System.currentTimeMillis());
        dto.setDescription("Test request for AI suggest-tags integration test");
        MvcResult result = mockMvc.perform(post("/api/v1/requests")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createTag(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateTagDto(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode suggestTags(Long requestId, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/requests/{id}/ai/suggest-tags", requestId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * The stub provider suggests "login-issue" and "urgent".
     * If "login-issue" exists in dictionary, the response must return existingTagId
     * and isNew=false.
     */
    @Test
    void suggestTags_existingDictionaryTag_returnsExistingTagIdAndIsNewFalse() throws Exception {
        // Pre-create "login-issue" in the tag dictionary (exact name the stub returns)
        Long loginIssueTagId = createTag("login-issue");
        Long requestId = createRequest(userToken);

        JsonNode response = suggestTags(requestId, userToken);
        JsonNode tags = response.get("tags");
        assertThat(tags).isNotNull().isNotEmpty();

        // Find the "login-issue" suggestion
        JsonNode loginIssueSuggestion = null;
        for (JsonNode tag : tags) {
            if ("login-issue".equals(tag.get("name").asText())) {
                loginIssueSuggestion = tag;
                break;
            }
        }

        assertThat(loginIssueSuggestion).isNotNull();
        assertThat(loginIssueSuggestion.get("isNew").asBoolean()).isFalse();
        assertThat(loginIssueSuggestion.get("existingTagId")).isNotNull();
        assertThat(loginIssueSuggestion.get("existingTagId").asLong()).isEqualTo(loginIssueTagId);
    }

    /**
     * When a suggestion name does not exist in dictionary, isNew=true and
     * existingTagId is absent/null.
     */
    @Test
    void suggestTags_unknownTag_returnsIsNewTrueAndNullExistingTagId() throws Exception {
        Long requestId = createRequest(userToken);

        // Stub provider always returns "login-issue" and "urgent" — ensure they do NOT
        // exist in dict
        JsonNode response = suggestTags(requestId, userToken);
        JsonNode tags = response.get("tags");
        assertThat(tags).isNotNull().isNotEmpty();

        // At least one tag not in dictionary → isNew=true
        boolean foundNewTag = false;
        for (JsonNode tag : tags) {
            if (tag.get("isNew").asBoolean()) {
                foundNewTag = true;
                // existingTagId should be null or absent
                assertThat(tag.has("existingTagId") && !tag.get("existingTagId").isNull())
                        .as("existingTagId should be null for isNew=true tag").isFalse();
                break;
            }
        }
        assertThat(foundNewTag).as("At least one tag should be isNew=true when not in dictionary").isTrue();
    }

    /**
     * Deduplication: verify that even if provider returns duplicate names
     * (simulated via reconcile),
     * apply the same tag twice is idempotent (no 400/409).
     */
    @Test
    void applyTag_secondApply_isIdempotentAndSucceeds() throws Exception {
        Long requestId = createRequest(userToken);
        Long tagId = createTag("DedupTag-" + System.currentTimeMillis());

        // First apply
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", requestId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tagId));

        // Second apply — must be idempotent (201 again, not 409 or 400)
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", requestId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tagId));
    }

    /**
     * Sequential multi-tag apply: apply two different AI-suggested tags in
     * sequence.
     * Both applies should succeed independently (no shared state leakage).
     */
    @Test
    void multiTag_applyTwoTagsSequentially_bothSucceed() throws Exception {
        Long requestId = createRequest(userToken);
        Long tag1 = createTag("MultiTag1-" + System.currentTimeMillis());
        Long tag2 = createTag("MultiTag2-" + System.currentTimeMillis());

        // Apply first tag
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", requestId, tag1)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // Apply second tag — must NOT fail
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", requestId, tag2)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // Both tags appear in the request's tag list
        mockMvc.perform(get("/api/v1/requests/{requestId}/tags", requestId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /**
     * Response contract: suggest-tags always returns tags[], runId, provider,
     * model.
     */
    @Test
    void suggestTags_responseContract_hasRequiredFields() throws Exception {
        Long requestId = createRequest(userToken);
        JsonNode response = suggestTags(requestId, userToken);

        assertThat(response.has("tags")).isTrue();
        assertThat(response.has("runId")).isTrue();
        assertThat(response.has("provider")).isTrue();
        assertThat(response.has("model")).isTrue();
        assertThat(response.get("tags").isArray()).isTrue();
    }
}
