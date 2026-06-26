package com.asms.workflow.api.dto;

import com.asms.workflow.domain.ApprovalRequest;
import com.asms.workflow.domain.ApprovalStatus;
import com.asms.workflow.domain.WorkflowType;

import java.time.Instant;
import java.util.Map;

public record ApprovalResponse(
        String id,
        String requesterId,
        String societyId,
        WorkflowType workflowType,
        ApprovalStatus status,
        String documentType,
        String description,
        Map<String, Object> metadata,
        String rejectionReason,
        Instant createdAt
) {
    public static ApprovalResponse from(ApprovalRequest r) {
        return new ApprovalResponse(r.getId(), r.getRequesterId(), r.getSocietyId(),
                r.getWorkflowType(), r.getStatus(), r.getDocumentType(), r.getDescription(),
                r.getMetadata(), r.getRejectionReason(), r.getCreatedAt());
    }
}
