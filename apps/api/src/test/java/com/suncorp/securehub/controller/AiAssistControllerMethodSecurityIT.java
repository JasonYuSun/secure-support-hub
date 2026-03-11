package com.suncorp.securehub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suncorp.securehub.dto.AiSummarizeResponseDto;
import com.suncorp.securehub.service.AiAssistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AiAssistControllerMethodSecurityIT {

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

    @MockBean
    AiAssistService aiAssistService;

    @Test
    void summarize_guestRole_isForbiddenBeforeServiceLayer() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", 1L)
                .with(user("guest").roles("GUEST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(aiAssistService);
    }

    @Test
    void summarize_userRole_isAllowed() throws Exception {
        when(aiAssistService.summarize(eq(1L), any(), eq("user"), anySet()))
                .thenReturn(AiSummarizeResponseDto.builder()
                        .summary("ok")
                        .runId("run-1")
                        .provider("stub")
                        .model("stub-model-id")
                        .latencyMs(10L)
                        .generatedAt(OffsetDateTime.now())
                        .build());

        mockMvc.perform(post("/api/v1/requests/{id}/ai/summarize", 1L)
                .with(user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        verify(aiAssistService).summarize(eq(1L), any(), eq("user"), anySet());
    }
}
