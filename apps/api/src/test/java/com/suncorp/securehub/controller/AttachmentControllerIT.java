package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.AttachmentUploadUrlRequestDto;
import com.suncorp.securehub.dto.CreateCommentDto;
import com.suncorp.securehub.dto.CreateRequestDto;
import com.suncorp.securehub.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AttachmentControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.attachments.bucket-name", () -> "securehub-test-attachments");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    S3Client s3Client;

    @MockBean
    S3Presigner s3Presigner;

    private String userToken;
    private String triageToken;

    @BeforeEach
    void setUp() throws Exception {
        userToken = login("user", "password");
        triageToken = login("triage", "password");

        PresignedPutObjectRequest putRequest = Mockito.mock(PresignedPutObjectRequest.class);
        when(putRequest.url()).thenReturn(new URL("https://example.com/upload"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(putRequest);

        PresignedGetObjectRequest getRequest = Mockito.mock(PresignedGetObjectRequest.class);
        when(getRequest.url()).thenReturn(new URL("https://example.com/download"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(getRequest);

        when(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(1024L).build());
    }

    @Test
    void requestAttachmentFlow_asOwner_shouldSucceed() throws Exception {
        Long requestId = createRequest(userToken, "Attachment request", "Needs file");

        AttachmentUploadUrlRequestDto uploadDto = new AttachmentUploadUrlRequestDto();
        uploadDto.setFileName("proof.pdf");
        uploadDto.setContentType("application/pdf");
        uploadDto.setFileSize(1024L);

        MvcResult uploadResult = mockMvc.perform(post("/api/v1/requests/{requestId}/attachments/upload-url", requestId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentId").isNumber())
                .andExpect(jsonPath("$.uploadUrl").value("https://example.com/upload"))
                .andReturn();

        Long attachmentId = readJson(uploadResult).get("attachmentId").asLong();

        mockMvc.perform(post("/api/v1/requests/{requestId}/attachments/{attachmentId}/confirm", requestId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/requests/{requestId}/attachments", requestId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachmentId))
                .andExpect(jsonPath("$[0].state").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/requests/{requestId}/attachments/{attachmentId}/download-url", requestId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").value(attachmentId))
                .andExpect(jsonPath("$.downloadUrl").value("https://example.com/download"));
    }

    @Test
    void requestAttachmentUpload_forAnotherUsersRequest_shouldReturn403() throws Exception {
        Long triageOwnedRequestId = createRequest(triageToken, "Triage request", "Owner should be triage");

        AttachmentUploadUrlRequestDto uploadDto = new AttachmentUploadUrlRequestDto();
        uploadDto.setFileName("proof.pdf");
        uploadDto.setContentType("application/pdf");
        uploadDto.setFileSize(1024L);

        mockMvc.perform(post("/api/v1/requests/{requestId}/attachments/upload-url", triageOwnedRequestId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void commentAttachmentFlow_asOwner_shouldSucceed() throws Exception {
        Long requestId = createRequest(userToken, "Request with comments", "Comment attachment test");
        Long commentId = createComment(requestId, userToken, "Adding comment for attachment.");

        AttachmentUploadUrlRequestDto uploadDto = new AttachmentUploadUrlRequestDto();
        uploadDto.setFileName("note.txt");
        uploadDto.setContentType("text/plain");
        uploadDto.setFileSize(1024L);

        MvcResult uploadResult = mockMvc.perform(post(
                        "/api/v1/requests/{requestId}/comments/{commentId}/attachments/upload-url", requestId, commentId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentId").isNumber())
                .andReturn();

        Long attachmentId = readJson(uploadResult).get("attachmentId").asLong();

        mockMvc.perform(post(
                        "/api/v1/requests/{requestId}/comments/{commentId}/attachments/{attachmentId}/confirm",
                        requestId, commentId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/requests/{requestId}/comments/{commentId}/attachments", requestId, commentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(attachmentId))
                .andExpect(jsonPath("$[0].commentId").value(commentId));

        mockMvc.perform(get(
                        "/api/v1/requests/{requestId}/comments/{commentId}/attachments/{attachmentId}/download-url",
                        requestId, commentId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://example.com/download"));
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

        return readJson(result).get("accessToken").asText();
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

        return readJson(result).get("id").asLong();
    }

    private Long createComment(Long requestId, String token, String body) throws Exception {
        CreateCommentDto commentDto = new CreateCommentDto();
        commentDto.setBody(body);

        MvcResult result = mockMvc.perform(post("/api/v1/requests/{id}/comments", requestId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentDto)))
                .andExpect(status().isCreated())
                .andReturn();

        return readJson(result).get("id").asLong();
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
