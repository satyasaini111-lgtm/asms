package com.asms.workflow.api.dto;

import com.asms.workflow.domain.WorkflowType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SubmitApprovalRequest(
        @NotBlank String requesterId,
        @NotBlank String societyId,
        @NotNull WorkflowType workflowType,
        @NotBlank String documentType,
        String description,
        Map<String, Object> metadata
) {}
