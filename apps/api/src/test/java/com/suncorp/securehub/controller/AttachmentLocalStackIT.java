package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AttachmentLocalStackIT {

    private static final String ATTACHMENT_BUCKET_NAME = "securehub-it-" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
            .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("app.attachments.bucket-name", () -> ATTACHMENT_BUCKET_NAME);
        registry.add("app.attachments.aws-region", localStack::getRegion);
        registry.add("app.attachments.aws-s3-endpoint",
                () -> localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("app.attachments.aws-access-key-id", localStack::getAccessKey);
        registry.add("app.attachments.aws-secret-access-key", localStack::getSecretKey);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private S3Client adminS3Client;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        adminS3Client = S3Client.builder()
                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localStack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        ensureBucketExists();
        userToken = login("user", "password");
    }

    @Test
    void requestAttachmentFlow_withLocalStack_shouldUploadConfirmDownloadAndDelete() throws Exception {
        Long requestId = createRequest(userToken, "S3 integration flow", "LocalStack attachment integration test");

        MvcResult uploadResult = mockMvc.perform(post("/api/v1/requests/{requestId}/attachments/upload-url", requestId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "evidence.txt",
                                "contentType", "text/plain",
                                "fileSize", 12
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentId").isNumber())
                .andExpect(jsonPath("$.uploadUrl").isString())
                .andReturn();

        JsonNode uploadJson = readJson(uploadResult);
        Long attachmentId = uploadJson.get("attachmentId").asLong();
        String uploadUrl = uploadJson.get("uploadUrl").asText();

        HttpRequest uploadRequest = HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray("hello world!".getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(uploadResponse.statusCode()).isEqualTo(200);

        mockMvc.perform(post("/api/v1/requests/{requestId}/attachments/{attachmentId}/confirm", requestId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        String requestPrefix = "requests/" + requestId + "/";
        assertThat(countObjects(requestPrefix)).isGreaterThan(0);

        mockMvc.perform(get("/api/v1/requests/{requestId}/attachments/{attachmentId}/download-url", requestId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").isString());

        mockMvc.perform(delete("/api/v1/requests/{requestId}/attachments/{attachmentId}", requestId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/requests/{requestId}/attachments", requestId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        assertThat(countObjects(requestPrefix)).isEqualTo(0);
    }

    @Test
    void deleteRequest_shouldCleanupS3ObjectsInLocalStack() throws Exception {
        Long requestId = createRequest(userToken, "Delete request cleanup", "Deleting request should cleanup attachments");

        MvcResult uploadResult = mockMvc.perform(post("/api/v1/requests/{requestId}/attachments/upload-url", requestId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fileName", "cleanup.txt",
                                "contentType", "text/plain",
                                "fileSize", 12
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode uploadJson = readJson(uploadResult);
        Long attachmentId = uploadJson.get("attachmentId").asLong();
        String uploadUrl = uploadJson.get("uploadUrl").asText();

        HttpRequest uploadRequest = HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray("cleanup file".getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(uploadResponse.statusCode()).isEqualTo(200);

        mockMvc.perform(post("/api/v1/requests/{requestId}/attachments/{attachmentId}/confirm", requestId, attachmentId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        String requestPrefix = "requests/" + requestId + "/";
        assertThat(countObjects(requestPrefix)).isGreaterThan(0);

        mockMvc.perform(delete("/api/v1/requests/{requestId}", requestId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/requests/{requestId}", requestId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        assertThat(countObjects(requestPrefix)).isEqualTo(0);
    }

    private long countObjects(String prefix) {
        return adminS3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(ATTACHMENT_BUCKET_NAME)
                        .prefix(prefix)
                        .build())
                .contents()
                .size();
    }

    private void ensureBucketExists() {
        try {
            adminS3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(ATTACHMENT_BUCKET_NAME)
                    .build());
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // Bucket already exists for this test run.
        }
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

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
