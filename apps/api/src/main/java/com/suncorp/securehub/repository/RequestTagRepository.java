package com.suncorp.securehub.repository;

import com.suncorp.securehub.entity.RequestTag;
import com.suncorp.securehub.entity.RequestTag.RequestTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RequestTagRepository extends JpaRepository<RequestTag, RequestTagId> {

    @Query("SELECT rt FROM RequestTag rt JOIN FETCH rt.tag t WHERE rt.id.requestId = :requestId AND t.deletedAt IS NULL")
    List<RequestTag> findActiveByRequestId(@Param("requestId") Long requestId);

    void deleteById(RequestTagId id);
}
