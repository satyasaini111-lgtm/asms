package com.asms.support.application;

import com.asms.support.api.dto.CreateTicketRequest;
import com.asms.support.api.dto.TicketResponse;
import com.asms.support.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request, String userId);
    TicketResponse getTicketById(String id);
    Page<TicketResponse> listTickets(String societyId, TicketStatus status, String userId, String role, Pageable pageable);
    TicketResponse assignTicket(String id, String assignedToUserId, String actorId);
    TicketResponse resolveTicket(String id, String resolutionNote, String actorId);
    TicketResponse closeTicket(String id, String actorId);
}
