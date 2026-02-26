package com.suncorp.securehub.service;

import com.suncorp.securehub.dto.UpdateUserRolesDto;
import com.suncorp.securehub.dto.UserDto;
import com.suncorp.securehub.entity.Role;
import com.suncorp.securehub.entity.User;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.RoleRepository;
import com.suncorp.securehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getId))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto getUser(Long id) {
        return toDto(findUserById(id));
    }

    @Transactional(readOnly = true)
    public List<String> listRoles() {
        return roleRepository.findAll().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();
    }

    @Transactional
    public UserDto updateUserRoles(Long id, UpdateUserRolesDto dto, String actorUsername) {
        User user = findUserById(id);
        User actor = findUserByUsername(actorUsername);
        Set<Role.RoleName> requestedRoles = dto.getRoles().stream()
                .map(this::toRoleName)
                .collect(Collectors.toSet());

        if (actor.getId().equals(user.getId()) && !requestedRoles.contains(Role.RoleName.ADMIN)) {
            throw new BadRequestException("You cannot remove your own ADMIN role");
        }

        List<Role> roles = roleRepository.findByNameIn(requestedRoles);
        Set<Role.RoleName> found = roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> missingRoles = requestedRoles.stream()
                .filter(roleName -> !found.contains(roleName))
                .map(Role.RoleName::name)
                .collect(Collectors.toSet());
        if (!missingRoles.isEmpty()) {
            throw new BadRequestException("Unknown role(s): " + String.join(", ", missingRoles));
        }

        user.setRoles(new HashSet<>(roles));
        return toDto(userRepository.save(user));
    }

    private Role.RoleName toRoleName(String rawRole) {
        try {
            return Role.RoleName.valueOf(rawRole.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BadRequestException("Invalid role: " + rawRole);
        }
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toSet()))
                .build();
    }
}
