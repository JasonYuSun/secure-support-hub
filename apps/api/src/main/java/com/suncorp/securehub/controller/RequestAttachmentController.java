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
@RequestMapping("/api/v1/requests/{requestId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Request Attachments", description = "Attachment endpoints for support requests")
@SecurityRequirement(name = "Bearer Authentication")
public class RequestAttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping("/upload-url")
    @Operation(summary = "Create upload URL for request attachment")
    public ResponseEntity<AttachmentUploadUrlResponseDto> createUploadUrl(
            @PathVariable Long requestId,
            @Valid @RequestBody AttachmentUploadUrlRequestDto dto,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.createRequestUploadUrl(
                requestId, dto, principal.getUsername(), roles(principal)));
    }

    @PostMapping("/{attachmentId}/confirm")
    @Operation(summary = "Confirm request attachment upload")
    public ResponseEntity<AttachmentDto> confirmUpload(
            @PathVariable Long requestId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(attachmentService.confirmRequestAttachment(
                requestId, attachmentId, principal.getUsername(), roles(principal)));
    }

    @GetMapping
    @Operation(summary = "List request attachments")
    public ResponseEntity<List<AttachmentDto>> listAttachments(
            @PathVariable Long requestId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(attachmentService.listRequestAttachments(
                requestId, principal.getUsername(), roles(principal)));
    }

    @GetMapping("/{attachmentId}/download-url")
    @Operation(summary = "Get download URL for request attachment")
    public ResponseEntity<AttachmentDownloadUrlResponseDto> getDownloadUrl(
            @PathVariable Long requestId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(attachmentService.getRequestDownloadUrl(
                requestId, attachmentId, principal.getUsername(), roles(principal)));
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(summary = "Delete request attachment")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long requestId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        attachmentService.deleteRequestAttachment(requestId, attachmentId, principal.getUsername(), roles(principal));
        return ResponseEntity.noContent().build();
    }

    private Set<String> roles(UserDetails principal) {
        return principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }
}
