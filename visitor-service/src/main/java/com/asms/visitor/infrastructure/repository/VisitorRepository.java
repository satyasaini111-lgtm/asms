package com.asms.visitor.infrastructure.repository;

import com.asms.visitor.domain.VisitorRequest;
import com.asms.visitor.domain.VisitorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VisitorRepository extends MongoRepository<VisitorRequest, String> {
    Page<VisitorRequest> findBySocietyId(String societyId, Pageable pageable);
    Page<VisitorRequest> findByResidentUserId(String residentUserId, Pageable pageable);
    List<VisitorRequest> findByStatusAndExpiresAtBefore(VisitorStatus status, Instant now);
}
