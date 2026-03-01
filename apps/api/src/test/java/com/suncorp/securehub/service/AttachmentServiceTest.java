package com.suncorp.securehub.service;

import com.suncorp.securehub.config.AttachmentProperties;
import com.suncorp.securehub.dto.AttachmentUploadUrlRequestDto;
import com.suncorp.securehub.entity.Role;
import com.suncorp.securehub.entity.SupportRequest;
import com.suncorp.securehub.entity.User;
import com.suncorp.securehub.exception.AccessDeniedException;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.repository.AttachmentRepository;
import com.suncorp.securehub.repository.CommentRepository;
import com.suncorp.securehub.repository.SupportRequestRepository;
import com.suncorp.securehub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private SupportRequestRepository requestRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private AttachmentService attachmentService;
    private AttachmentProperties attachmentProperties;

    private User ownerUser;
    private User otherUser;
    private SupportRequest ownerRequest;

    @BeforeEach
    void setUp() {
        attachmentProperties = new AttachmentProperties();
        attachmentProperties.setMaxFileSizeBytes(10 * 1024 * 1024L);

        attachmentService = new AttachmentService(
                attachmentRepository,
                requestRepository,
                commentRepository,
                userRepository,
                s3Client,
                s3Presigner,
                attachmentProperties
        );

        Role userRole = Role.builder().id(1L).name(Role.RoleName.USER).build();
        ownerUser = User.builder()
                .id(1L)
                .username("owner")
                .email("owner@example.com")
                .roles(Set.of(userRole))
                .build();
        otherUser = User.builder()
                .id(2L)
                .username("other")
                .email("other@example.com")
                .roles(Set.of(userRole))
                .build();

        ownerRequest = SupportRequest.builder()
                .id(100L)
                .title("Request")
                .description("Description")
                .createdBy(ownerUser)
                .build();
    }

    @Test
    void createRequestUploadUrl_whenFileIsTooLarge_shouldThrowBadRequest() {
        AttachmentUploadUrlRequestDto dto = new AttachmentUploadUrlRequestDto();
        dto.setFileName("large.pdf");
        dto.setContentType("application/pdf");
        dto.setFileSize(attachmentProperties.getMaxFileSizeBytes() + 1);

        when(requestRepository.findById(ownerRequest.getId())).thenReturn(Optional.of(ownerRequest));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));

        assertThatThrownBy(() -> attachmentService.createRequestUploadUrl(
                ownerRequest.getId(), dto, "owner", Set.of("ROLE_USER")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File exceeds max size");

        verify(attachmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRequestUploadUrl_whenMimeTypeNotAllowed_shouldThrowBadRequest() {
        AttachmentUploadUrlRequestDto dto = new AttachmentUploadUrlRequestDto();
        dto.setFileName("archive.zip");
        dto.setContentType("application/zip");
        dto.setFileSize(1024L);

        when(requestRepository.findById(ownerRequest.getId())).thenReturn(Optional.of(ownerRequest));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));

        assertThatThrownBy(() -> attachmentService.createRequestUploadUrl(
                ownerRequest.getId(), dto, "owner", Set.of("ROLE_USER")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Content type is not allowed");

        verify(attachmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRequestUploadUrl_whenUserIsNotOwnerAndNotTriage_shouldThrowAccessDenied() {
        AttachmentUploadUrlRequestDto dto = new AttachmentUploadUrlRequestDto();
        dto.setFileName("proof.pdf");
        dto.setContentType("application/pdf");
        dto.setFileSize(1024L);

        SupportRequest othersRequest = SupportRequest.builder()
                .id(200L)
                .title("Other")
                .description("Other")
                .createdBy(otherUser)
                .build();

        when(requestRepository.findById(othersRequest.getId())).thenReturn(Optional.of(othersRequest));

        assertThatThrownBy(() -> attachmentService.createRequestUploadUrl(
                othersRequest.getId(), dto, "owner", Set.of("ROLE_USER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have access");

        verify(userRepository, never()).findByUsername("owner");
        verify(attachmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
