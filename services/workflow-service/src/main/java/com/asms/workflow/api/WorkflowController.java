package com.asms.workflow.api;

import com.asms.workflow.api.dto.ApprovalResponse;
import com.asms.workflow.api.dto.SubmitApprovalRequest;
import com.asms.workflow.application.WorkflowService;
import com.asms.workflow.domain.ApprovalStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public ResponseEntity<ApprovalResponse> submit(@Valid @RequestBody SubmitApprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.submit(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApprovalResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.getById(id));
    }

    @GetMapping("/requester/{requesterId}")
    public ResponseEntity<Page<ApprovalResponse>> listByRequester(
            @PathVariable String requesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(workflowService.listByRequester(requesterId, PageRequest.of(page, size)));
    }

    @GetMapping("/society/{societyId}")
    public ResponseEntity<Page<ApprovalResponse>> listBySociety(
            @PathVariable String societyId,
            @RequestParam(defaultValue = "PENDING") ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(workflowService.listBySociety(societyId, status, PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<ApprovalResponse> process(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.process(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApprovalResponse> reject(
            @PathVariable String id,
            @RequestParam String reason) {
        return ResponseEntity.ok(workflowService.reject(id, reason));
    }
}
