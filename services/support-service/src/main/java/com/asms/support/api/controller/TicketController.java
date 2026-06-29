package com.asms.support.api.controller;

import com.asms.support.api.dto.CreateTicketRequest;
import com.asms.support.api.dto.TicketResponse;
import com.asms.support.application.TicketService;
import com.asms.support.domain.TicketStatus;
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
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Support ticket management")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
    @Operation(summary = "Raise a support ticket")
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @RequestHeader("X-User-Id") String userId) {
        TicketResponse response = ticketService.createTicket(request, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER', 'SUPPORT_STAFF', 'OWNER', 'TENANT')")
    @Operation(summary = "Get ticket by ID")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable String id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER', 'SUPPORT_STAFF', 'OWNER', 'TENANT')")
    @Operation(summary = "List tickets")
    public ResponseEntity<Page<TicketResponse>> listTickets(
            @RequestParam(required = false) String societyId,
            @RequestParam(required = false) TicketStatus status,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ticketService.listTickets(societyId, status, userId, role, pageable));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER')")
    @Operation(summary = "Assign ticket to support staff")
    public ResponseEntity<TicketResponse> assignTicket(
            @PathVariable String id,
            @RequestParam String assignedToUserId,
            @RequestHeader("X-User-Id") String actorId) {
        return ResponseEntity.ok(ticketService.assignTicket(id, assignedToUserId, actorId));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER', 'SUPPORT_STAFF')")
    @Operation(summary = "Resolve a ticket")
    public ResponseEntity<TicketResponse> resolveTicket(
            @PathVariable String id,
            @RequestParam String resolutionNote,
            @RequestHeader("X-User-Id") String actorId) {
        return ResponseEntity.ok(ticketService.resolveTicket(id, resolutionNote, actorId));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER')")
    @Operation(summary = "Close a resolved ticket")
    public ResponseEntity<TicketResponse> closeTicket(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String actorId) {
        return ResponseEntity.ok(ticketService.closeTicket(id, actorId));
    }
}
