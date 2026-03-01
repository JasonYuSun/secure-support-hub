package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.CreateCommentDto;
import com.suncorp.securehub.dto.CreateRequestDto;
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
class CommentControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String userToken;
    private String triageToken;
    private Long requestId;

    @BeforeEach
    void setUp() throws Exception {
        userToken = login("user", "password");
        triageToken = login("triage", "password");
        requestId = createRequest(userToken, "Test request for comments", "A description");
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

        var node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private Long createComment(Long reqId, String token, String body) throws Exception {
        CreateCommentDto comment = new CreateCommentDto();
        comment.setBody(body);

        MvcResult result = mockMvc.perform(post("/api/v1/requests/{id}/comments", reqId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comment)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void addComment_asAuthor_shouldReturn201() throws Exception {
        CreateCommentDto comment = new CreateCommentDto();
        comment.setBody("This is my comment on the request.");

        mockMvc.perform(post("/api/v1/requests/{id}/comments", requestId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comment)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.body").value("This is my comment on the request."))
                .andExpect(jsonPath("$.author.username").value("user"));
    }

    @Test
    void addComment_withoutAuth_shouldReturn403() throws Exception {
        CreateCommentDto comment = new CreateCommentDto();
        comment.setBody("Unauthenticated comment attempt.");

        mockMvc.perform(post("/api/v1/requests/{id}/comments", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comment)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addComment_withBlankBody_shouldReturn400() throws Exception {
        CreateCommentDto comment = new CreateCommentDto();
        comment.setBody("");

        mockMvc.perform(post("/api/v1/requests/{id}/comments", requestId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comment)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addComment_onNonExistentRequest_shouldReturn404() throws Exception {
        CreateCommentDto comment = new CreateCommentDto();
        comment.setBody("Comment on a ghost request.");

        mockMvc.perform(post("/api/v1/requests/{id}/comments", 99999L)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comment)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listComments_asAuthor_shouldReturnPagedResults() throws Exception {
        // Post a comment first
        CreateCommentDto comment = new CreateCommentDto();
        comment.setBody("Listed comment.");
        mockMvc.perform(post("/api/v1/requests/{id}/comments", requestId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comment)))
                .andExpect(status().isCreated());

        // Then list
        mockMvc.perform(get("/api/v1/requests/{id}/comments", requestId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].body").value("Listed comment."));
    }

    @Test
    void deleteComment_asAuthor_shouldReturn204() throws Exception {
        Long commentId = createComment(requestId, userToken, "Delete me.");

        mockMvc.perform(delete("/api/v1/requests/{requestId}/comments/{commentId}", requestId, commentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/requests/{id}/comments", requestId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void deleteComment_asAnotherPlainUser_shouldReturn403() throws Exception {
        Long triageRequestId = createRequest(triageToken, "Triage request", "Owned by triage");
        Long triageCommentId = createComment(triageRequestId, triageToken, "Triage authored comment");

        mockMvc.perform(delete("/api/v1/requests/{requestId}/comments/{commentId}", triageRequestId, triageCommentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
