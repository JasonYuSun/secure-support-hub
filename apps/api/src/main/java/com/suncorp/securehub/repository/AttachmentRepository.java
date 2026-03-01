package com.suncorp.securehub.repository;

import com.suncorp.securehub.entity.Attachment;
import com.suncorp.securehub.entity.AttachmentState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    Optional<Attachment> findByIdAndRequest_Id(Long id, Long requestId);
    Optional<Attachment> findByIdAndComment_Id(Long id, Long commentId);

    List<Attachment> findByRequest_IdOrderByCreatedAtAsc(Long requestId);
    List<Attachment> findByComment_Request_IdOrderByCreatedAtAsc(Long requestId);
    List<Attachment> findByComment_IdOrderByCreatedAtAsc(Long commentId);
    List<Attachment> findByStateAndCreatedAtBefore(AttachmentState state, LocalDateTime createdAt);

    long countByRequest_IdAndStateIn(Long requestId, Collection<AttachmentState> states);
    long countByComment_IdAndStateIn(Long commentId, Collection<AttachmentState> states);
}
