package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/requests/{requestId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comment endpoints for support requests")
@SecurityRequirement(name = "Bearer Authentication")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "List comments for a request")
    public ResponseEntity<Page<CommentDto>> listComments(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(commentService.listComments(requestId, page, size));
    }

    @PostMapping
    @Operation(summary = "Add a comment to a request")
    public ResponseEntity<CommentDto> addComment(
            @PathVariable Long requestId,
            @Valid @RequestBody CreateCommentDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(requestId, dto, principal.getUsername(), roles));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete comment (author or TRIAGE/ADMIN)")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long requestId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        commentService.deleteComment(requestId, commentId, principal.getUsername(), roles);
        return ResponseEntity.noContent().build();
    }
}
