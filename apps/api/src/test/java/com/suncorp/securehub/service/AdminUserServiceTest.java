package com.suncorp.securehub.service;

import com.suncorp.securehub.dto.UpdateUserRolesDto;
import com.suncorp.securehub.dto.UserDto;
import com.suncorp.securehub.entity.Role;
import com.suncorp.securehub.entity.User;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.RoleRepository;
import com.suncorp.securehub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks private AdminUserService service;

    private User user;
    private User admin;
    private Role userRole;
    private Role triageRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRole = Role.builder().id(1L).name(Role.RoleName.USER).build();
        triageRole = Role.builder().id(2L).name(Role.RoleName.TRIAGE).build();
        adminRole = Role.builder().id(3L).name(Role.RoleName.ADMIN).build();
        user = User.builder()
                .id(10L)
                .username("user")
                .email("user@example.com")
                .roles(Set.of(userRole))
                .build();
        admin = User.builder()
                .id(20L)
                .username("admin")
                .email("admin@example.com")
                .roles(Set.of(adminRole, userRole))
                .build();
    }

    @Test
    void listUsers_shouldReturnMappedDtos() {
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserDto> users = service.listUsers();

        assertThat(users).hasSize(1);
        assertThat(users.getFirst().getUsername()).isEqualTo("user");
        assertThat(users.getFirst().getRoles()).containsExactly("USER");
    }

    @Test
    void getUser_shouldReturnMappedDto() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        UserDto found = service.getUser(10L);

        assertThat(found.getId()).isEqualTo(10L);
        assertThat(found.getUsername()).isEqualTo("user");
    }

    @Test
    void listRoles_shouldReturnRoleNames() {
        Role adminRole = Role.builder().id(3L).name(Role.RoleName.ADMIN).build();
        when(roleRepository.findAll()).thenReturn(List.of(triageRole, userRole, adminRole));

        List<String> roles = service.listRoles();

        assertThat(roles).containsExactly("ADMIN", "TRIAGE", "USER");
    }

    @Test
    void updateUserRoles_shouldReplaceRoles() {
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("USER", "TRIAGE"));

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(roleRepository.findByNameIn(Set.of(Role.RoleName.USER, Role.RoleName.TRIAGE)))
                .thenReturn(List.of(userRole, triageRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        UserDto updated = service.updateUserRoles(10L, dto, "admin");

        assertThat(updated.getRoles()).containsExactlyInAnyOrder("USER", "TRIAGE");
    }

    @Test
    void updateUserRoles_withInvalidRole_shouldThrow() {
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("NOT_A_ROLE"));

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateUserRoles(10L, dto, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid role");
    }

    @Test
    void updateUserRoles_withMissingUser_shouldThrow() {
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("USER"));

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUserRoles(99L, dto, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateUserRoles_selfDemotion_shouldThrow() {
        UpdateUserRolesDto dto = new UpdateUserRolesDto();
        dto.setRoles(Set.of("USER"));

        when(userRepository.findById(20L)).thenReturn(Optional.of(admin));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateUserRoles(20L, dto, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot remove your own ADMIN role");
    }
}
