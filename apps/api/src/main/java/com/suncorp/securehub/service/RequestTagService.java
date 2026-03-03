package com.suncorp.securehub.service;

import com.suncorp.securehub.dto.TagDto;
import com.suncorp.securehub.dto.UserDto;
import com.suncorp.securehub.entity.RequestTag;
import com.suncorp.securehub.entity.RequestTag.RequestTagId;
import com.suncorp.securehub.entity.SupportRequest;
import com.suncorp.securehub.entity.Tag;
import com.suncorp.securehub.entity.User;
import com.suncorp.securehub.exception.AccessDeniedException;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.RequestTagRepository;
import com.suncorp.securehub.repository.SupportRequestRepository;
import com.suncorp.securehub.repository.TagRepository;
import com.suncorp.securehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestTagService {

    private final SupportRequestRepository requestRepository;
    private final TagRepository tagRepository;
    private final RequestTagRepository requestTagRepository;
    private final UserRepository userRepository;

    private static final Set<String> TRIAGE_ROLES = Set.of("ROLE_TRIAGE", "ROLE_ADMIN");

    @Transactional(readOnly = true)
    public List<TagDto> listRequestTags(Long requestId, String username, Set<String> roles) {
        SupportRequest request = requireRequestAccess(requestId, username, roles);
        return requestTagRepository.findActiveByRequestId(request.getId()).stream()
                .map(rt -> toTagDto(rt.getTag()))
                .collect(Collectors.toList());
    }

    @Transactional
    public TagDto applyTag(Long requestId, Long tagId, String username, Set<String> roles) {
        SupportRequest request = requireRequestAccess(requestId, username, roles);
        Tag tag = tagRepository.findByIdAndDeletedAtIsNull(tagId)
                .orElseThrow(
                        () -> new BadRequestException("Tag with id " + tagId + " does not exist or has been deleted"));

        User appliedBy = findUserByUsername(username);
        RequestTagId compositeId = new RequestTagId(request.getId(), tag.getId());

        // Idempotent: if already applied, just return existing tag
        Optional<RequestTag> existing = requestTagRepository.findById(compositeId);
        if (existing.isEmpty()) {
            RequestTag requestTag = RequestTag.builder()
                    .id(compositeId)
                    .request(request)
                    .tag(tag)
                    .appliedBy(appliedBy)
                    .build();
            requestTagRepository.save(requestTag);
        }
        return toTagDto(tag);
    }

    @Transactional
    public void unapplyTag(Long requestId, Long tagId, String username, Set<String> roles) {
        requireRequestAccess(requestId, username, roles);
        RequestTagId compositeId = new RequestTagId(requestId, tagId);
        // No-op if not applied
        if (requestTagRepository.existsById(compositeId)) {
            requestTagRepository.deleteById(compositeId);
        }
    }

    private SupportRequest requireRequestAccess(Long requestId, String username, Set<String> roles) {
        SupportRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupportRequest", "id", requestId));
        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        if (!isTriage && !request.getCreatedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("You do not have access to this request");
        }
        return request;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private TagDto toTagDto(Tag tag) {
        User creator = tag.getCreatedBy();
        return TagDto.builder()
                .id(tag.getId())
                .name(tag.getName())
                .createdBy(UserDto.builder()
                        .id(creator.getId())
                        .username(creator.getUsername())
                        .email(creator.getEmail())
                        .roles(creator.getRoles().stream()
                                .map(r -> r.getName().name())
                                .collect(Collectors.toSet()))
                        .build())
                .createdAt(tag.getCreatedAt())
                .build();
    }
}
