package com.asms.workflow.application;

import com.asms.workflow.api.dto.ApprovalResponse;
import com.asms.workflow.api.dto.SubmitApprovalRequest;
import com.asms.workflow.domain.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkflowService {
    ApprovalResponse submit(SubmitApprovalRequest request);
    ApprovalResponse getById(String id);
    Page<ApprovalResponse> listByRequester(String requesterId, Pageable pageable);
    Page<ApprovalResponse> listBySociety(String societyId, ApprovalStatus status, Pageable pageable);
    ApprovalResponse process(String id);
    ApprovalResponse reject(String id, String reason);
}
