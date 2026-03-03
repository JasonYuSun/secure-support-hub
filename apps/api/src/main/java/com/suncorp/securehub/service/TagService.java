package com.suncorp.securehub.service;

import com.suncorp.securehub.dto.CreateTagDto;
import com.suncorp.securehub.dto.TagDto;
import com.suncorp.securehub.dto.UserDto;
import com.suncorp.securehub.entity.Tag;
import com.suncorp.securehub.entity.User;
import com.suncorp.securehub.exception.AccessDeniedException;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.TagRepository;
import com.suncorp.securehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    private static final Set<String> TRIAGE_ROLES = Set.of("ROLE_TRIAGE", "ROLE_ADMIN");

    @Transactional(readOnly = true)
    public List<TagDto> listTags() {
        return tagRepository.findByDeletedAtIsNull().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TagDto createTag(CreateTagDto dto, String username, Set<String> roles) {
        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        if (!isTriage) {
            throw new AccessDeniedException("Only TRIAGE or ADMIN roles can create tags");
        }
        String normalizedName = dto.getName().trim().toLowerCase();
        if (tagRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(normalizedName)) {
            throw new BadRequestException("Tag with name '" + dto.getName().trim() + "' already exists");
        }
        User creator = findUserByUsername(username);
        Tag tag = Tag.builder()
                .name(dto.getName().trim())
                .createdBy(creator)
                .build();
        return toDto(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(Long tagId, String username, Set<String> roles) {
        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        if (!isTriage) {
            throw new AccessDeniedException("Only TRIAGE or ADMIN roles can delete tags");
        }
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", "id", tagId));
        if (tag.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Tag", "id", tagId);
        }
        tag.setDeletedAt(LocalDateTime.now());
        tagRepository.save(tag);
    }

    public TagDto toDto(Tag tag) {
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

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }
}
