package com.asms.visitor.api.controller;

import com.asms.visitor.api.dto.VisitorEntryRequest;
import com.asms.visitor.application.VisitorService;
import com.asms.visitor.domain.VisitorRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/visitors")
@Tag(name = "Visitors", description = "Visitor entry management")
public class VisitorController {

    private final VisitorService visitorService;

    public VisitorController(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SECURITY')")
    @Operation(summary = "Log visitor entry request")
    public ResponseEntity<VisitorRequest> requestEntry(
            @Valid @RequestBody VisitorEntryRequest request,
            @RequestHeader("X-User-Id") String securityUserId) {
        VisitorRequest vr = visitorService.requestEntry(request, securityUserId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(vr.getId()).toUri();
        return ResponseEntity.created(location).body(vr);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
    @Operation(summary = "Resident approves visitor")
    public ResponseEntity<VisitorRequest> approve(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String residentId) {
        return ResponseEntity.ok(visitorService.approve(id, residentId));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
    @Operation(summary = "Resident rejects visitor")
    public ResponseEntity<VisitorRequest> reject(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String residentId) {
        return ResponseEntity.ok(visitorService.reject(id, residentId));
    }

    @PatchMapping("/{id}/checkin")
    @PreAuthorize("hasRole('SECURITY')")
    @Operation(summary = "Security checks in approved visitor")
    public ResponseEntity<VisitorRequest> checkIn(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String securityId) {
        return ResponseEntity.ok(visitorService.checkIn(id, securityId));
    }

    @PatchMapping("/{id}/checkout")
    @PreAuthorize("hasRole('SECURITY')")
    @Operation(summary = "Security checks out visitor")
    public ResponseEntity<VisitorRequest> checkOut(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String securityId) {
        return ResponseEntity.ok(visitorService.checkOut(id, securityId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER', 'SECURITY')")
    @Operation(summary = "List visitor requests for a society")
    public ResponseEntity<Page<VisitorRequest>> listBySociety(
            @RequestParam String societyId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(visitorService.listBySociety(societyId, pageable));
    }
}
