package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.service.AttachmentService;
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
@RequestMapping("/api/v1/requests/{requestId}/comments/{commentId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Comment Attachments", description = "Attachment endpoints for request comments")
@SecurityRequirement(name = "Bearer Authentication")
public class CommentAttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping("/upload-url")
    @Operation(summary = "Create upload URL for comment attachment")
    public ResponseEntity<AttachmentUploadUrlResponseDto> createUploadUrl(
            @PathVariable Long requestId,
            @PathVariable Long commentId,
            @Valid @RequestBody AttachmentUploadUrlRequestDto dto,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.createCommentUploadUrl(
                requestId, commentId, dto, principal.getUsername(), roles(principal)));
    }

    @PostMapping("/{attachmentId}/confirm")
    @Operation(summary = "Confirm comment attachment upload")
    public ResponseEntity<AttachmentDto> confirmUpload(
            @PathVariable Long requestId,
            @PathVariable Long commentId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(attachmentService.confirmCommentAttachment(
                requestId, commentId, attachmentId, principal.getUsername(), roles(principal)));
    }

    @GetMapping
    @Operation(summary = "List comment attachments")
    public ResponseEntity<List<AttachmentDto>> listAttachments(
            @PathVariable Long requestId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(attachmentService.listCommentAttachments(
                requestId, commentId, principal.getUsername(), roles(principal)));
    }

    @GetMapping("/{attachmentId}/download-url")
    @Operation(summary = "Get download URL for comment attachment")
    public ResponseEntity<AttachmentDownloadUrlResponseDto> getDownloadUrl(
            @PathVariable Long requestId,
            @PathVariable Long commentId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(attachmentService.getCommentDownloadUrl(
                requestId, commentId, attachmentId, principal.getUsername(), roles(principal)));
    }

    private Set<String> roles(UserDetails principal) {
        return principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }
}
