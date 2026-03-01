package com.suncorp.securehub.service;

import com.suncorp.securehub.config.AttachmentProperties;
import com.suncorp.securehub.dto.*;
import com.suncorp.securehub.entity.*;
import com.suncorp.securehub.exception.AccessDeniedException;
import com.suncorp.securehub.exception.BadRequestException;
import com.suncorp.securehub.exception.ResourceNotFoundException;
import com.suncorp.securehub.repository.AttachmentRepository;
import com.suncorp.securehub.repository.CommentRepository;
import com.suncorp.securehub.repository.SupportRequestRepository;
import com.suncorp.securehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Set<String> TRIAGE_ROLES = Set.of("ROLE_TRIAGE", "ROLE_ADMIN");
    private static final Set<AttachmentState> COUNTED_STATES = Set.of(AttachmentState.PENDING, AttachmentState.ACTIVE);
    private static final int MAX_FILE_NAME_LENGTH = 120;

    private final AttachmentRepository attachmentRepository;
    private final SupportRequestRepository requestRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AttachmentProperties attachmentProperties;

    @Transactional
    public AttachmentUploadUrlResponseDto createRequestUploadUrl(
            Long requestId,
            AttachmentUploadUrlRequestDto dto,
            String username,
            Set<String> roles
    ) {
        SupportRequest request = findRequestAndAuthorize(requestId, username, roles);
        User uploader = findUserByUsername(username);
        String contentType = validateUploadRequest(dto);
        enforceRequestAttachmentLimit(requestId);
        return createUploadUrlAttachment(request.getId(), request, null, uploader, dto, contentType);
    }

    @Transactional
    public AttachmentUploadUrlResponseDto createCommentUploadUrl(
            Long requestId,
            Long commentId,
            AttachmentUploadUrlRequestDto dto,
            String username,
            Set<String> roles
    ) {
        Comment comment = findCommentAndAuthorize(requestId, commentId, username, roles);
        User uploader = findUserByUsername(username);
        String contentType = validateUploadRequest(dto);
        enforceCommentAttachmentLimit(commentId);
        return createUploadUrlAttachment(comment.getRequest().getId(), null, comment, uploader, dto, contentType);
    }

    @Transactional
    public AttachmentDto confirmRequestAttachment(Long requestId, Long attachmentId, String username, Set<String> roles) {
        findRequestAndAuthorize(requestId, username, roles);
        Attachment attachment = attachmentRepository.findByIdAndRequest_Id(attachmentId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));
        return toDto(confirmAttachment(attachment));
    }

    @Transactional
    public AttachmentDto confirmCommentAttachment(
            Long requestId,
            Long commentId,
            Long attachmentId,
            String username,
            Set<String> roles
    ) {
        findCommentAndAuthorize(requestId, commentId, username, roles);
        Attachment attachment = attachmentRepository.findByIdAndComment_Id(attachmentId, commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));
        return toDto(confirmAttachment(attachment));
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listRequestAttachments(Long requestId, String username, Set<String> roles) {
        findRequestAndAuthorize(requestId, username, roles);
        return attachmentRepository.findByRequest_IdOrderByCreatedAtAsc(requestId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listCommentAttachments(Long requestId, Long commentId, String username, Set<String> roles) {
        findCommentAndAuthorize(requestId, commentId, username, roles);
        return attachmentRepository.findByComment_IdOrderByCreatedAtAsc(commentId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadUrlResponseDto getRequestDownloadUrl(
            Long requestId,
            Long attachmentId,
            String username,
            Set<String> roles
    ) {
        findRequestAndAuthorize(requestId, username, roles);
        Attachment attachment = attachmentRepository.findByIdAndRequest_Id(attachmentId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));
        return toDownloadUrlResponse(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadUrlResponseDto getCommentDownloadUrl(
            Long requestId,
            Long commentId,
            Long attachmentId,
            String username,
            Set<String> roles
    ) {
        findCommentAndAuthorize(requestId, commentId, username, roles);
        Attachment attachment = attachmentRepository.findByIdAndComment_Id(attachmentId, commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));
        return toDownloadUrlResponse(attachment);
    }

    private AttachmentUploadUrlResponseDto createUploadUrlAttachment(
            Long requestId,
            SupportRequest request,
            Comment comment,
            User uploader,
            AttachmentUploadUrlRequestDto dto,
            String normalizedContentType
    ) {
        String sanitizedFileName = sanitizeFileName(dto.getFileName());

        Attachment attachment = Attachment.builder()
                .request(request)
                .comment(comment)
                .fileName(sanitizedFileName)
                .contentType(normalizedContentType)
                .fileSize(dto.getFileSize())
                .state(AttachmentState.PENDING)
                .uploadedBy(uploader)
                .s3ObjectKey("pending")
                .build();
        attachment = attachmentRepository.save(attachment);

        String objectKey = buildObjectKey(requestId, comment != null ? comment.getId() : null,
                attachment.getId(), sanitizedFileName);
        attachment.setS3ObjectKey(objectKey);
        attachment = attachmentRepository.save(attachment);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(attachmentProperties.getBucketName())
                .key(objectKey)
                .contentType(normalizedContentType)
                .contentLength(dto.getFileSize())
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(attachmentProperties.getUploadUrlTtl())
                .putObjectRequest(putObjectRequest)
                .build());

        return AttachmentUploadUrlResponseDto.builder()
                .attachmentId(attachment.getId())
                .uploadUrl(presignedRequest.url().toString())
                .expiresAt(Instant.now().plus(attachmentProperties.getUploadUrlTtl()))
                .build();
    }

    private Attachment confirmAttachment(Attachment attachment) {
        if (attachment.getState() == AttachmentState.ACTIVE) {
            return attachment;
        }
        if (attachment.getState() == AttachmentState.FAILED) {
            throw new BadRequestException("Attachment is in FAILED state and cannot be confirmed");
        }

        try {
            HeadObjectResponse headObjectResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(attachmentProperties.getBucketName())
                    .key(attachment.getS3ObjectKey())
                    .build());

            if (headObjectResponse.contentLength() == null
                    || !headObjectResponse.contentLength().equals(attachment.getFileSize())) {
                markAsFailed(attachment);
                throw new BadRequestException("Uploaded file size does not match metadata");
            }
        } catch (NoSuchKeyException ex) {
            markAsFailed(attachment);
            throw new BadRequestException("Attachment object was not found in S3");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                markAsFailed(attachment);
                throw new BadRequestException("Attachment object was not found in S3");
            }
            throw ex;
        }

        attachment.setState(AttachmentState.ACTIVE);
        return attachmentRepository.save(attachment);
    }

    private AttachmentDownloadUrlResponseDto toDownloadUrlResponse(Attachment attachment) {
        if (attachment.getState() != AttachmentState.ACTIVE) {
            throw new BadRequestException("Attachment is not ready for download");
        }

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(attachmentProperties.getDownloadUrlTtl())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(attachmentProperties.getBucketName())
                        .key(attachment.getS3ObjectKey())
                        .responseContentType(attachment.getContentType())
                        .build())
                .build());

        return AttachmentDownloadUrlResponseDto.builder()
                .attachmentId(attachment.getId())
                .downloadUrl(presignedRequest.url().toString())
                .expiresAt(Instant.now().plus(attachmentProperties.getDownloadUrlTtl()))
                .build();
    }

    private String validateUploadRequest(AttachmentUploadUrlRequestDto dto) {
        if (dto.getFileSize() > attachmentProperties.getMaxFileSizeBytes()) {
            throw new BadRequestException(
                    "File exceeds max size of " + attachmentProperties.getMaxFileSizeBytes() + " bytes");
        }

        String normalizedContentType = normalizeContentType(dto.getContentType());
        Set<String> allowedMimeTypes = attachmentProperties.getAllowedMimeTypes().stream()
                .map(this::normalizeContentType)
                .collect(Collectors.toSet());
        if (!allowedMimeTypes.contains(normalizedContentType)) {
            throw new BadRequestException("Content type is not allowed: " + dto.getContentType());
        }

        return normalizedContentType;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String mime = contentType.split(";")[0].trim();
        return mime.toLowerCase(Locale.ROOT);
    }

    private void enforceRequestAttachmentLimit(Long requestId) {
        long existing = attachmentRepository.countByRequest_IdAndStateIn(requestId, COUNTED_STATES);
        if (existing >= attachmentProperties.getRequestMaxCount()) {
            throw new BadRequestException("Request attachment limit exceeded (" + attachmentProperties.getRequestMaxCount() + ")");
        }
    }

    private void enforceCommentAttachmentLimit(Long commentId) {
        long existing = attachmentRepository.countByComment_IdAndStateIn(commentId, COUNTED_STATES);
        if (existing >= attachmentProperties.getCommentMaxCount()) {
            throw new BadRequestException("Comment attachment limit exceeded (" + attachmentProperties.getCommentMaxCount() + ")");
        }
    }

    private SupportRequest findRequestAndAuthorize(Long requestId, String username, Set<String> roles) {
        SupportRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SupportRequest", "id", requestId));

        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        if (!isTriage && !request.getCreatedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("You do not have access to this request attachments");
        }
        return request;
    }

    private Comment findCommentAndAuthorize(Long requestId, Long commentId, String username, Set<String> roles) {
        Comment comment = commentRepository.findByIdAndRequest_Id(commentId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        boolean isTriage = roles.stream().anyMatch(TRIAGE_ROLES::contains);
        if (!isTriage && !comment.getRequest().getCreatedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("You do not have access to this comment attachments");
        }
        return comment;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private void markAsFailed(Attachment attachment) {
        attachment.setState(AttachmentState.FAILED);
        attachmentRepository.save(attachment);
    }

    private String buildObjectKey(Long requestId, Long commentId, Long attachmentId, String fileName) {
        if (commentId == null) {
            return "requests/" + requestId + "/attachments/" + attachmentId + "/" + fileName;
        }
        return "requests/" + requestId + "/comments/" + commentId + "/attachments/" + attachmentId + "/" + fileName;
    }

    private String sanitizeFileName(String fileName) {
        String basename = StringUtils.getFilename(fileName);
        if (!StringUtils.hasText(basename)) {
            basename = "file";
        }
        String sanitized = basename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "file";
        }
        if (sanitized.length() <= MAX_FILE_NAME_LENGTH) {
            return sanitized;
        }

        int extensionIndex = sanitized.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex >= sanitized.length() - 1) {
            return sanitized.substring(0, MAX_FILE_NAME_LENGTH);
        }

        String extension = sanitized.substring(extensionIndex);
        int maxBaseLength = MAX_FILE_NAME_LENGTH - extension.length();
        if (maxBaseLength <= 0) {
            return sanitized.substring(0, MAX_FILE_NAME_LENGTH);
        }
        return sanitized.substring(0, maxBaseLength) + extension;
    }

    private AttachmentDto toDto(Attachment attachment) {
        return AttachmentDto.builder()
                .id(attachment.getId())
                .requestId(resolveRequestId(attachment))
                .commentId(attachment.getComment() != null ? attachment.getComment().getId() : null)
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .state(attachment.getState())
                .uploadedBy(toUserDto(attachment.getUploadedBy()))
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    private Long resolveRequestId(Attachment attachment) {
        if (attachment.getRequest() != null) {
            return attachment.getRequest().getId();
        }
        if (attachment.getComment() != null && attachment.getComment().getRequest() != null) {
            return attachment.getComment().getRequest().getId();
        }
        return null;
    }

    private UserDto toUserDto(User user) {
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
