package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.entity.SupportRequest.RequestStatus;
import com.suncorp.securehub.service.SupportRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
@Tag(name = "Support Requests", description = "Support request management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class SupportRequestController {

    private final SupportRequestService requestService;

    @GetMapping
    @Operation(summary = "List support requests with optional filters")
    public ResponseEntity<Page<SupportRequestDto>> listRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal UserDetails principal) {

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(dir, sort));
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());

        return ResponseEntity.ok(
                requestService.listRequests(status, assignedTo, principal.getUsername(), roles, pageable));
    }

    @PostMapping
    @Operation(summary = "Create a new support request")
    public ResponseEntity<SupportRequestDto> createRequest(
            @Valid @RequestBody CreateRequestDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestService.createRequest(dto, principal.getUsername()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a support request by ID")
    public ResponseEntity<SupportRequestDto> getRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        return ResponseEntity.ok(requestService.getRequest(id, principal.getUsername(), roles));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update status or assignee (TRIAGE/ADMIN only)")
    public ResponseEntity<SupportRequestDto> updateRequest(
            @PathVariable Long id,
            @RequestBody UpdateRequestDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        return ResponseEntity.ok(requestService.updateRequest(id, dto, principal.getUsername(), roles));
    }
}
