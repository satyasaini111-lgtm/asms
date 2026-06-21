package com.asms.support.api.dto;

import com.asms.support.domain.Ticket;
import com.asms.support.domain.TicketCategory;
import com.asms.support.domain.TicketPriority;
import com.asms.support.domain.TicketStatus;

import java.time.Instant;

public record TicketResponse(
        String id,
        String societyId,
        String raisedByUserId,
        String title,
        String description,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        String assignedToUserId,
        String resolutionNote,
        Instant createdAt,
        Instant updatedAt
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getSocietyId(),
                ticket.getRaisedByUserId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getAssignedToUserId(),
                ticket.getResolutionNote(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
