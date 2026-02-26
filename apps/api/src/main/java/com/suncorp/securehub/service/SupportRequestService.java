package com.suncorp.securehub.service;

import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.entity.*;
import com.suncorp.securehub.entity.SupportRequest.RequestStatus;
import com.suncorp.securehub.exception.AccessDeniedException;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportRequestService {

    private final SupportRequestRepository requestRepository;
    private final UserRepository userRepository;

    private static final Set<String> TRIAGE_ROLES = Set.of("ROLE_TRIAGE", "ROLE_ADMIN");

    @Transactional
    public SupportRequestDto createRequest(CreateRequestDto dto, String username) {
        User creator = findUserByUsername(username);
        SupportRequest req = SupportRequest.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(RequestStatus.OPEN)
                .createdBy(creator)
                .build();
        return toDto(requestRepository.save(req));
    }

    @Transactional(readOnly = true)
    public Page<SupportRequestDto> listRequests(RequestStatus status, Long assignedTo,
                                                 String username, Set<String> roles, Pageable pageable) {
        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        if (isTriage) {
            return requestRepository.findWithFilters(status, assignedTo, pageable).map(this::toDto);
        } else {
            User user = findUserByUsername(username);
            return requestRepository.findByCreatedByWithFilters(user.getId(), status, pageable).map(this::toDto);
        }
    }

    @Transactional(readOnly = true)
    public SupportRequestDto getRequest(Long id, String username, Set<String> roles) {
        SupportRequest req = findById(id);
        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        if (!isTriage && !req.getCreatedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("You do not have access to this request");
        }
        return toDto(req);
    }

    @Transactional
    public SupportRequestDto updateRequest(Long id, UpdateRequestDto dto, String username, Set<String> roles) {
        SupportRequest req = findById(id);
        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);

        if (!isTriage) {
            throw new AccessDeniedException("Only TRIAGE or ADMIN roles can update requests");
        }

        if (dto.getStatus() != null) {
            validateStatusTransition(req.getStatus(), dto.getStatus());
            req.setStatus(dto.getStatus());
        }

        if (dto.getAssignedToId() != null) {
            User assignee = userRepository.findById(dto.getAssignedToId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", dto.getAssignedToId()));
            req.setAssignedTo(assignee);
        }

        return toDto(requestRepository.save(req));
    }

    private void validateStatusTransition(RequestStatus current, RequestStatus next) {
        boolean valid = switch (current) {
            case OPEN        -> next == RequestStatus.IN_PROGRESS || next == RequestStatus.CLOSED;
            case IN_PROGRESS -> next == RequestStatus.RESOLVED || next == RequestStatus.CLOSED;
            case RESOLVED    -> next == RequestStatus.CLOSED;
            case CLOSED      -> false;
        };
        if (!valid) {
            throw new BadRequestException("Invalid status transition: " + current + " -> " + next);
        }
    }

    private SupportRequest findById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupportRequest", "id", id));
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public SupportRequestDto toDto(SupportRequest r) {
        return SupportRequestDto.builder()
                .id(r.getId())
                .title(r.getTitle())
                .description(r.getDescription())
                .status(r.getStatus())
                .createdBy(toUserDto(r.getCreatedBy()))
                .assignedTo(r.getAssignedTo() != null ? toUserDto(r.getAssignedTo()) : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .commentCount(r.getComments() != null ? r.getComments().size() : 0)
                .build();
    }

    private UserDto toUserDto(User u) {
        return UserDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .roles(u.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toSet()))
                .build();
    }
}
