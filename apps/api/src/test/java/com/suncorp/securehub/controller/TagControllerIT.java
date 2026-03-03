package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class TagControllerIT {

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
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        userToken = login("user", "password");
        triageToken = login("triage", "password");
        adminToken = login("admin", "password");
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

        var node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("accessToken").asText();
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
    void listTags_asUser_shouldReturnEmptyOrExistingTags() throws Exception {
        mockMvc.perform(get("/api/v1/tags")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listTags_withoutAuth_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTag_asTriage_shouldReturn201() throws Exception {
        // Use a unique name to avoid conflicts with other tests
        String tagName = "BillingIT-" + System.currentTimeMillis();
        CreateTagDto dto = new CreateTagDto(tagName);

        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(tagName))
                .andExpect(jsonPath("$.createdBy.username").value("triage"));
    }

    @Test
    void createTag_asAdmin_shouldReturn201() throws Exception {
        String tagName = "AdminTag-" + System.currentTimeMillis();
        CreateTagDto dto = new CreateTagDto(tagName);

        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(tagName));
    }

    @Test
    void createTag_asUser_shouldReturn403() throws Exception {
        CreateTagDto dto = new CreateTagDto("UserShouldNotCreate-" + System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTag_duplicateName_shouldReturn400() throws Exception {
        String tagName = "DuplicateTag-" + System.currentTimeMillis();
        CreateTagDto dto = new CreateTagDto(tagName);

        // First creation succeeds
        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Second creation with same name fails
        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void createTag_duplicateNameCaseInsensitive_shouldReturn400() throws Exception {
        String tagName = "CaseTag-" + System.currentTimeMillis();
        CreateTagDto lower = new CreateTagDto(tagName.toLowerCase());
        CreateTagDto upper = new CreateTagDto(tagName.toUpperCase());

        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lower)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(upper)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTag_asTriage_shouldReturn204() throws Exception {
        Long tagId = createTag(triageToken, "ToDelete-" + System.currentTimeMillis());

        mockMvc.perform(delete("/api/v1/tags/{tagId}", tagId)
                .header("Authorization", "Bearer " + triageToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTag_asUser_shouldReturn403() throws Exception {
        Long tagId = createTag(triageToken, "ToDeleteByUser-" + System.currentTimeMillis());

        mockMvc.perform(delete("/api/v1/tags/{tagId}", tagId)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTag_nonExistent_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/v1/tags/{tagId}", 99999L)
                .header("Authorization", "Bearer " + triageToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedTag_doesNotAppearInList() throws Exception {
        String tagName = "ThenDeleted-" + System.currentTimeMillis();
        Long tagId = createTag(triageToken, tagName);

        // Soft-delete it
        mockMvc.perform(delete("/api/v1/tags/{tagId}", tagId)
                .header("Authorization", "Bearer " + triageToken))
                .andExpect(status().isNoContent());

        // Should not appear in list
        mockMvc.perform(get("/api/v1/tags")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + tagId + ")]").isEmpty());
    }

    @Test
    void createTag_withBlankName_shouldReturn400() throws Exception {
        CreateTagDto dto = new CreateTagDto("");

        mockMvc.perform(post("/api/v1/tags")
                .header("Authorization", "Bearer " + triageToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    Long createTagHelper(String tagName) throws Exception {
        return createTag(triageToken, tagName);
    }
}
