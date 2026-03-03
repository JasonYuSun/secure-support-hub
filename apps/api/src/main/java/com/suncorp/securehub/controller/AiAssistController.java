package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.AiActionRequestDto;
import com.suncorp.securehub.dto.AiDraftResponseDto;
import com.suncorp.securehub.dto.AiSuggestTagsResponseDto;
import com.suncorp.securehub.dto.AiSummarizeResponseDto;
import com.suncorp.securehub.service.AiAssistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/requests/{id}/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assist", description = "AI-powered tools for support requests")
public class AiAssistController {

    private final AiAssistService aiAssistService;

    @PostMapping("/summarize")
    @Operation(summary = "Summarize request")
    public AiSummarizeResponseDto summarize(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AiActionRequestDto reqDto,
            Authentication auth) {
        return aiAssistService.summarize(id, reqDto, auth.getName(), extractRoles(auth));
    }

    @PostMapping("/suggest-tags")
    @Operation(summary = "Suggest tags for request")
    public AiSuggestTagsResponseDto suggestTags(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AiActionRequestDto reqDto,
            Authentication auth) {
        return aiAssistService.suggestTags(id, reqDto, auth.getName(), extractRoles(auth));
    }

    @PostMapping("/draft-response")
    @Operation(summary = "Draft a response for request")
    public AiDraftResponseDto draftResponse(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AiActionRequestDto reqDto,
            Authentication auth) {
        return aiAssistService.draftResponse(id, reqDto, auth.getName(), extractRoles(auth));
    }

    private Set<String> extractRoles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
