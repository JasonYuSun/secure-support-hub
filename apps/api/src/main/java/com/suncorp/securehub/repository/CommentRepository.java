package com.suncorp.securehub.repository;

import com.suncorp.securehub.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByRequestId(Long requestId, Pageable pageable);
    Optional<Comment> findByIdAndRequest_Id(Long id, Long requestId);
}
