package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.CreateRequestDto;
import com.suncorp.securehub.dto.LoginRequest;
import com.suncorp.securehub.entity.AiAssistRun;
import com.suncorp.securehub.repository.AiAssistRunRepository;
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

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AI-002 audit minimization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AiAuditSnapshotIT {

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
    AiAssistRunRepository aiAssistRunRepository;

    private String userToken;
    private Long requestId;
    private String sensitiveTitle;
    private String sensitiveDescription;
    private String sensitiveHint;

    @BeforeEach
    void setUp() throws Exception {
        userToken = login("user", "password");
        sensitiveTitle = "TITLE_MARKER_" + UUID.randomUUID();
        sensitiveDescription = "DESC_MARKER_" + UUID.randomUUID();
        sensitiveHint = "RAW_HINT_MARKER_" + UUID.randomUUID();
        requestId = createRequest(userToken, sensitiveTitle, sensitiveDescription);
    }

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

    private Long createRequest(String token, String title, String description) throws Exception {
        CreateRequestDto dto = new CreateRequestDto();
        dto.setTitle(title);
        dto.setDescription(description);
        MvcResult result = mockMvc.perform(post("/api/v1/requests")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private AiAssistRun latestRun(String actionType) {
        return aiAssistRunRepository.findByRequestId(requestId).stream()
                .filter(run -> actionType.equals(run.getActionType()))
                .max(Comparator.comparing(AiAssistRun::getCreatedAt))
                .orElseThrow(() -> new AssertionError("No run found for action " + actionType));
    }

    private void assertSnapshotsDoNotContainRawSensitiveContent(AiAssistRun run) {
        assertThat(run.getInputSnapshot()).isNotNull();
        assertThat(run.getOutputPayload()).isNotNull();

        assertThat(run.getInputSnapshot())
                .doesNotContain(sensitiveTitle)
                .doesNotContain(sensitiveDescription)
                .doesNotContain(sensitiveHint);

        assertThat(run.getOutputPayload())
                .doesNotContain(sensitiveTitle)
                .doesNotContain(sensitiveDescription)
                .doesNotContain(sensitiveHint);
    }

    @Test
    void summarize_runPersistsMetadataOnly_noRawPromptOrSummaryText() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("promptOverride", sensitiveHint))))
                .andExpect(status().isOk());

        AiAssistRun run = latestRun("SUMMARIZE");
        assertSnapshotsDoNotContainRawSensitiveContent(run);

        JsonNode input = objectMapper.readTree(run.getInputSnapshot());
        JsonNode output = objectMapper.readTree(run.getOutputPayload());

        assertThat(input.get("requestId").asLong()).isEqualTo(requestId);
        assertThat(input.has("rawHintSha256")).isTrue();
        assertThat(input.get("rawHintSha256").asText()).hasSize(64);
        assertThat(input.has("requestTitle")).isFalse();
        assertThat(input.has("requestDescription")).isFalse();

        assertThat(output.has("summaryLength")).isTrue();
        assertThat(output.has("summary")).isFalse();
    }

    @Test
    void draft_runPersistsMetadataOnly_noRawDraftText() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/ai/draft-response", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("promptOverride", sensitiveHint))))
                .andExpect(status().isOk());

        AiAssistRun run = latestRun("DRAFT_RESPONSE");
        assertSnapshotsDoNotContainRawSensitiveContent(run);

        JsonNode output = objectMapper.readTree(run.getOutputPayload());
        assertThat(output.has("draftLength")).isTrue();
        assertThat(output.has("draft")).isFalse();
    }

    @Test
    void suggestTags_runPersistsMetadataOnly_noRawTagPayload() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/ai/suggest-tags", requestId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("promptOverride", sensitiveHint))))
                .andExpect(status().isOk());

        AiAssistRun run = latestRun("SUGGEST_TAGS");
        assertSnapshotsDoNotContainRawSensitiveContent(run);

        JsonNode output = objectMapper.readTree(run.getOutputPayload());
        assertThat(output.has("tagCount")).isTrue();
        assertThat(output.has("newTagCount")).isTrue();
        assertThat(output.has("tags")).isFalse();
    }
}
