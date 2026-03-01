package com.suncorp.securehub.service;

import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.entity.*;
import com.suncorp.securehub.exception.AccessDeniedException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final SupportRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;

    private static final Set<String> TRIAGE_ROLES = Set.of("ROLE_TRIAGE", "ROLE_ADMIN");

    @Transactional
    public CommentDto addComment(Long requestId, CreateCommentDto dto, String username, Set<String> roles) {
        SupportRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupportRequest", "id", requestId));

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        boolean isOwner  = req.getCreatedBy().getUsername().equals(username);
        if (!isTriage && !isOwner) {
            throw new AccessDeniedException("You cannot comment on this request");
        }

        Comment comment = Comment.builder()
                .request(req)
                .author(author)
                .body(dto.getBody())
                .build();

        return toDto(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> listComments(Long requestId, int page, int size) {
        if (!requestRepository.existsById(requestId)) {
            throw new ResourceNotFoundException("SupportRequest", "id", requestId);
        }
        return commentRepository.findByRequestId(requestId,
                PageRequest.of(page, size, Sort.by("createdAt").ascending()))
                .map(this::toDto);
    }

    @Transactional
    public void deleteComment(Long requestId, Long commentId, String username, Set<String> roles) {
        Comment comment = commentRepository.findByIdAndRequest_Id(commentId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        boolean isAuthor = comment.getAuthor().getUsername().equals(username);
        if (!isTriage && !isAuthor) {
            throw new AccessDeniedException("Only comment author, TRIAGE, or ADMIN can delete this comment");
        }

        attachmentService.deleteAllForComment(commentId);
        commentRepository.delete(comment);
    }

    private CommentDto toDto(Comment c) {
        return CommentDto.builder()
                .id(c.getId())
                .requestId(c.getRequest().getId())
                .author(UserDto.builder()
                        .id(c.getAuthor().getId())
                        .username(c.getAuthor().getUsername())
                        .email(c.getAuthor().getEmail())
                        .roles(c.getAuthor().getRoles().stream()
                                .map(r -> r.getName().name())
                                .collect(Collectors.toSet()))
                        .build())
                .body(c.getBody())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
