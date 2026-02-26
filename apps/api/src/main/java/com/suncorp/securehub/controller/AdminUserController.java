package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.UpdateUserRolesDto;
import com.suncorp.securehub.dto.UserDto;
import com.suncorp.securehub.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Users", description = "User and role management endpoints (ADMIN only)")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public ResponseEntity<List<UserDto>> listUsers() {
        return ResponseEntity.ok(adminUserService.listUsers());
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a user by ID")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    @PatchMapping("/users/{id}/roles")
    @Operation(summary = "Replace user roles")
    public ResponseEntity<UserDto> updateUserRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(adminUserService.updateUserRoles(id, dto, principal.getUsername()));
    }

    @GetMapping("/roles")
    @Operation(summary = "List available roles")
    public ResponseEntity<List<String>> listRoles() {
        return ResponseEntity.ok(adminUserService.listRoles());
    }
}
