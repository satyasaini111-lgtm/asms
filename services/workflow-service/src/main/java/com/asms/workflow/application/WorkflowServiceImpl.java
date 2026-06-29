package com.asms.workflow.application;

import com.asms.workflow.api.dto.ApprovalResponse;
import com.asms.workflow.api.dto.SubmitApprovalRequest;
import com.asms.workflow.domain.ApprovalRequest;
import com.asms.workflow.domain.ApprovalStatus;
import com.asms.workflow.exception.ResourceNotFoundException;
import com.asms.workflow.infrastructure.WorkflowRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final ApprovalHandler approvalChain;

    public WorkflowServiceImpl(WorkflowRepository workflowRepository,
                                @Qualifier("vendorApprovalChain") ApprovalHandler approvalChain) {
        this.workflowRepository = workflowRepository;
        this.approvalChain = approvalChain;
    }

    @Override
    public ApprovalResponse submit(SubmitApprovalRequest req) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setRequesterId(req.requesterId());
        approval.setSocietyId(req.societyId());
        approval.setWorkflowType(req.workflowType());
        approval.setDocumentType(req.documentType());
        approval.setDescription(req.description());
        approval.setMetadata(req.metadata() != null ? req.metadata() : new HashMap<>());
        approval.setStatus(ApprovalStatus.PENDING);
        return ApprovalResponse.from(workflowRepository.save(approval));
    }

    @Override
    public ApprovalResponse getById(String id) {
        return ApprovalResponse.from(workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id)));
    }

    @Override
    public Page<ApprovalResponse> listByRequester(String requesterId, Pageable pageable) {
        return workflowRepository.findByRequesterId(requesterId, pageable).map(ApprovalResponse::from);
    }

    @Override
    public Page<ApprovalResponse> listBySociety(String societyId, ApprovalStatus status, Pageable pageable) {
        return workflowRepository.findBySocietyIdAndStatus(societyId, status, pageable)
                .map(ApprovalResponse::from);
    }

    @Override
    public ApprovalResponse process(String id) {
        ApprovalRequest approval = workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id));

        Map<String, Object> context = new HashMap<>(approval.getMetadata());
        context.put("documentType", approval.getDocumentType());
        context.put("requesterId", approval.getRequesterId());
        context.put("workflowType", approval.getWorkflowType().name());

        boolean approved = approvalChain.handle(context);
        approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setUpdatedAt(Instant.now());
        return ApprovalResponse.from(workflowRepository.save(approval));
    }

    @Override
    public ApprovalResponse reject(String id, String reason) {
        ApprovalRequest approval = workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id));
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setRejectionReason(reason);
        approval.setUpdatedAt(Instant.now());
        return ApprovalResponse.from(workflowRepository.save(approval));
    }
}
