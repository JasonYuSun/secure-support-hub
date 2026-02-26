package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.LoginRequest;
import com.suncorp.securehub.dto.UpdateUserRolesDto;
import com.suncorp.securehub.service.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminUserControllerIT {

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
    @SpyBean AdminUserService adminUserService;

    private String adminToken;
    private String userToken;

    @AfterEach
    void tearDown() {
        reset(adminUserService);
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "password");
        userToken = login("user", "password");
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

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    @Test
    void listUsers_asAdmin_shouldReturnSeededUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItems("user", "triage", "admin")));
    }

    @Test
    void listUsers_asNonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserRoles_asAdmin_shouldSucceed() throws Exception {
        Long userId = fetchUserId("user");
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("USER", "TRIAGE"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/roles", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.roles", hasItems("USER", "TRIAGE")));
    }

    @Test
    void updateUserRoles_withInvalidRole_shouldReturn400() throws Exception {
        Long userId = fetchUserId("user");
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("INVALID_ROLE"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/roles", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void getUser_asAdmin_shouldReturnUser() throws Exception {
        Long userId = fetchUserId("user");

        mockMvc.perform(get("/api/v1/admin/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    void listRoles_asAdmin_shouldReturnAllRoleNames() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("ADMIN", "TRIAGE", "USER")));
    }

    @Test
    void updateUserRoles_selfDemotion_shouldReturn400() throws Exception {
        Long adminId = fetchUserId("admin");
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("USER", "TRIAGE"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/roles", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("You cannot remove your own ADMIN role"));
    }

    @Test
    void updateUserRoles_whenOptimisticLockConflict_shouldReturn409() throws Exception {
        Long userId = fetchUserId("user");
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("USER", "TRIAGE"));

        doThrow(new OptimisticLockingFailureException("stale state"))
                .when(adminUserService)
                .updateUserRoles(eq(userId), any(UpdateUserRolesDto.class), eq("admin"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/roles", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("This user was updated by another operation. Please refresh and try again."));
    }

    private Long fetchUserId(String username) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode users = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode user : users) {
            if (username.equals(user.get("username").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("User not found in admin list response: " + username);
    }
}
