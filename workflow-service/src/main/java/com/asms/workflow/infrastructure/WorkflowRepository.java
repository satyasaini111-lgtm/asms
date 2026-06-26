package com.asms.workflow.infrastructure;

import com.asms.workflow.domain.ApprovalRequest;
import com.asms.workflow.domain.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WorkflowRepository extends MongoRepository<ApprovalRequest, String> {
    Page<ApprovalRequest> findByRequesterId(String requesterId, Pageable pageable);
    Page<ApprovalRequest> findBySocietyIdAndStatus(String societyId, ApprovalStatus status, Pageable pageable);
}
