package com.suncorp.securehub.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RequestTagControllerIT {

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

    private Long createTag(String token, String tagName) throws Exception {
        CreateTagDto dto = new CreateTagDto(tagName);

        MvcResult result = mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void applyTag_byOwner_shouldReturn201() throws Exception {
        Long reqId = createRequest(userToken, "Tag test req", "desc");
        Long tagId = createTag(triageToken, "Billing-RT-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tagId));
    }

    @Test
    void applyTag_idempotent_shouldReturn201OnDuplicateApply() throws Exception {
        Long reqId = createRequest(userToken, "Idempotent tag req", "desc");
        Long tagId = createTag(triageToken, "IdempTag-" + System.currentTimeMillis());

        // Apply first time
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // Apply again — should succeed (idempotent)
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());
    }

    @Test
    void applyTag_byTriage_onAnyRequest_shouldReturn201() throws Exception {
        Long userReqId = createRequest(userToken, "User req for triage tag", "desc");
        Long tagId = createTag(triageToken, "TriageApply-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", userReqId, tagId)
                .header("Authorization", "Bearer " + triageToken))
                .andExpect(status().isCreated());
    }

    @Test
    void applyTag_onOtherUsersRequest_shouldReturn403() throws Exception {
        Long triageReqId = createRequest(triageToken, "Triage-owned request", "desc");
        Long tagId = createTag(triageToken, "Forbidden-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", triageReqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void applyTag_softDeletedTag_shouldReturn400() throws Exception {
        Long reqId = createRequest(userToken, "Soft-delete tag req", "desc");
        Long tagId = createTag(triageToken, "ToBeDeleted-" + System.currentTimeMillis());

        // Soft-delete the tag
        mockMvc.perform(delete("/api/v1/tags/{tagId}", tagId)
                .header("Authorization", "Bearer " + triageToken))
                .andExpect(status().isNoContent());

        // Try to apply deleted tag
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRequestTags_returnsAppliedTags() throws Exception {
        Long reqId = createRequest(userToken, "List tags req", "desc");
        Long tagId = createTag(triageToken, "ListableTag-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/requests/{requestId}/tags", reqId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(tagId));
    }

    @Test
    void listRequestTags_onOtherUsersRequest_shouldReturn403() throws Exception {
        Long triageReqId = createRequest(triageToken, "Triage req for list", "desc");

        mockMvc.perform(get("/api/v1/requests/{requestId}/tags", triageReqId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unapplyTag_shouldReturn204AndTagDisappears() throws Exception {
        Long reqId = createRequest(userToken, "Unapply tag req", "desc");
        Long tagId = createTag(triageToken, "UnapplyTag-" + System.currentTimeMillis());

        // Apply first
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // Unapply
        mockMvc.perform(delete("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/v1/requests/{requestId}/tags", reqId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unapplyTag_notApplied_shouldReturn204NoOp() throws Exception {
        Long reqId = createRequest(userToken, "Unapply not applied req", "desc");
        Long tagId = createTag(triageToken, "NotApplied-" + System.currentTimeMillis());

        // Unapply without having applied first — should be no-op
        mockMvc.perform(delete("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void softDeletedTag_disappearsFromRequestTagsList() throws Exception {
        Long reqId = createRequest(userToken, "Soft-delete list req", "desc");
        Long tagId = createTag(triageToken, "SoftDeleteListTag-" + System.currentTimeMillis());

        // Apply tag
        mockMvc.perform(post("/api/v1/requests/{requestId}/tags/{tagId}", reqId, tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        // Soft-delete the tag from dictionary
        mockMvc.perform(delete("/api/v1/tags/{tagId}", tagId)
                .header("Authorization", "Bearer " + triageToken))
                .andExpect(status().isNoContent());

        // Tag should not appear in request's tag list (active only)
        mockMvc.perform(get("/api/v1/requests/{requestId}/tags", reqId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
