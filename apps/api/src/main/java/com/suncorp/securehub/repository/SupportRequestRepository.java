package com.suncorp.securehub.repository;

import com.suncorp.securehub.entity.SupportRequest;
import com.suncorp.securehub.entity.SupportRequest.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    Page<SupportRequest> findByCreatedById(Long userId, Pageable pageable);

    Page<SupportRequest> findByStatus(RequestStatus status, Pageable pageable);

    @Query("SELECT r FROM SupportRequest r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:assignedTo IS NULL OR r.assignedTo.id = :assignedTo)")
    Page<SupportRequest> findWithFilters(
            @Param("status") RequestStatus status,
            @Param("assignedTo") Long assignedTo,
            Pageable pageable);

    @Query("SELECT r FROM SupportRequest r WHERE r.createdBy.id = :userId AND " +
           "(:status IS NULL OR r.status = :status)")
    Page<SupportRequest> findByCreatedByWithFilters(
            @Param("userId") Long userId,
            @Param("status") RequestStatus status,
            Pageable pageable);
}
