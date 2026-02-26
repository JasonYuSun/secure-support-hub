package com.suncorp.securehub.service;

import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.entity.*;
import com.suncorp.securehub.entity.SupportRequest.RequestStatus;
import com.suncorp.securehub.exception.AccessDeniedException;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportRequestServiceTest {

    @Mock private SupportRequestRepository requestRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private SupportRequestService service;

    private User user;
    private User triagedUser;

    @BeforeEach
    void setUp() {
        Role userRole = Role.builder().id(1L).name(Role.RoleName.USER).build();
        Role triageRole = Role.builder().id(2L).name(Role.RoleName.TRIAGE).build();

        user = User.builder().id(1L).username("user").email("user@example.com")
                .roles(Set.of(userRole)).build();
        triagedUser = User.builder().id(2L).username("triage").email("triage@example.com")
                .roles(Set.of(triageRole)).build();
    }

    @Test
    void createRequest_shouldSaveAndReturnDto() {
        CreateRequestDto dto = new CreateRequestDto();
        dto.setTitle("Help needed");
        dto.setDescription("I need help with my account");

        SupportRequest saved = SupportRequest.builder()
                .id(1L).title(dto.getTitle()).description(dto.getDescription())
                .status(RequestStatus.OPEN).createdBy(user).build();

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(requestRepository.save(any())).thenReturn(saved);

        SupportRequestDto result = service.createRequest(dto, "user");
        assertThat(result.getTitle()).isEqualTo("Help needed");
        assertThat(result.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void updateRequest_validTransition_shouldSucceed() {
        SupportRequest existing = SupportRequest.builder()
                .id(1L).title("T").description("D").status(RequestStatus.OPEN)
                .createdBy(user).build();

        UpdateRequestDto dto = new UpdateRequestDto();
        dto.setStatus(RequestStatus.IN_PROGRESS);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupportRequestDto result = service.updateRequest(1L, dto, "triage",
                Set.of("ROLE_TRIAGE"));
        assertThat(result.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
    }

    @Test
    void updateRequest_invalidTransition_shouldThrow() {
        SupportRequest existing = SupportRequest.builder()
                .id(1L).title("T").description("D").status(RequestStatus.CLOSED)
                .createdBy(user).build();

        UpdateRequestDto dto = new UpdateRequestDto();
        dto.setStatus(RequestStatus.OPEN);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateRequest(1L, dto, "triage", Set.of("ROLE_TRIAGE")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void getRequest_asRegularUser_notOwner_shouldThrow() {
        SupportRequest req = SupportRequest.builder()
                .id(1L).title("T").description("D").status(RequestStatus.OPEN)
                .createdBy(triagedUser).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.getRequest(1L, "user", Set.of("ROLE_USER")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
