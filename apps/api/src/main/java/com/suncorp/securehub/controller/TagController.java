package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.CreateTagDto;
import com.suncorp.securehub.dto.TagDto;
import com.suncorp.securehub.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tag Dictionary", description = "Tag dictionary management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "List all active tags (all authenticated roles)")
    public ResponseEntity<List<TagDto>> listTags() {
        return ResponseEntity.ok(tagService.listTags());
    }

    @PostMapping
    @Operation(summary = "Create a new tag (TRIAGE/ADMIN only)")
    public ResponseEntity<TagDto> createTag(
            @Valid @RequestBody CreateTagDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        // RBAC enforced in service but we pass roles through for consistency
        // createTag is restricted to TRIAGE/ADMIN - enforce in service
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagService.createTag(dto, principal.getUsername(), roles));
    }

    @DeleteMapping("/{tagId}")
    @Operation(summary = "Soft-delete a tag (TRIAGE/ADMIN only)")
    public ResponseEntity<Void> deleteTag(
            @PathVariable Long tagId,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        tagService.deleteTag(tagId, principal.getUsername(), roles);
        return ResponseEntity.noContent().build();
    }
}
