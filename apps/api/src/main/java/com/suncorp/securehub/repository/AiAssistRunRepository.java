package com.suncorp.securehub.repository;

import com.suncorp.securehub.entity.AiAssistRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiAssistRunRepository extends JpaRepository<AiAssistRun, UUID> {
    List<AiAssistRun> findByRequestId(Long requestId);
}
