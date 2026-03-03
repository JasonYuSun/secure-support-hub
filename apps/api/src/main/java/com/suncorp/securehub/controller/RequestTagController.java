package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.TagDto;
import com.suncorp.securehub.service.RequestTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/requests/{requestId}/tags")
@RequiredArgsConstructor
@Tag(name = "Request Tags", description = "Tag apply/unapply on support requests")
@SecurityRequirement(name = "Bearer Authentication")
public class RequestTagController {

    private final RequestTagService requestTagService;

    @GetMapping
    @Operation(summary = "List tags applied to a request")
    public ResponseEntity<List<TagDto>> listRequestTags(
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        return ResponseEntity.ok(
                requestTagService.listRequestTags(requestId, principal.getUsername(), roles));
    }

    @PostMapping("/{tagId}")
    @Operation(summary = "Apply a tag to a request")
    public ResponseEntity<TagDto> applyTag(
            @PathVariable Long requestId,
            @PathVariable Long tagId,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestTagService.applyTag(requestId, tagId, principal.getUsername(), roles));
    }

    @DeleteMapping("/{tagId}")
    @Operation(summary = "Unapply a tag from a request")
    public ResponseEntity<Void> unapplyTag(
            @PathVariable Long requestId,
            @PathVariable Long tagId,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        requestTagService.unapplyTag(requestId, tagId, principal.getUsername(), roles);
        return ResponseEntity.noContent().build();
    }
}
