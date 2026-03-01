package com.suncorp.securehub.controller;

import com.suncorp.securehub.dto.UserDto;
import com.suncorp.securehub.entity.User;
import com.suncorp.securehub.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.suncorp.securehub.entity.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Standard user endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List users (filtered by role)")
    @PreAuthorize("hasRole('USER') or hasRole('TRIAGE') or hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> listUsers(@RequestParam(required = false) Role.RoleName role) {
        List<User> users;
        if (role != null) {
            users = userRepository.findByRoles_Name(role);
        } else {
            users = userRepository.findAll();
        }

        List<UserDto> dtos = users.stream()
                .map(u -> {
                    UserDto dto = new UserDto();
                    dto.setId(u.getId());
                    dto.setUsername(u.getUsername());
                    dto.setEmail(u.getEmail());
                    dto.setRoles(u.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toSet()));
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
