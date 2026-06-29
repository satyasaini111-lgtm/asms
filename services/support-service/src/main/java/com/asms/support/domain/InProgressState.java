package com.asms.support.domain;

public class InProgressState implements TicketState {

    @Override
    public void assign(Ticket ticket, String assignedTo) {
        ticket.setAssignedToUserId(assignedTo);
        ticket.addHistory(assignedTo, TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS, "Reassigned");
    }

    @Override
    public void resolve(Ticket ticket, String resolutionNote) {
        ticket.setResolutionNote(resolutionNote);
        ticket.addHistory(ticket.getAssignedToUserId(), TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, resolutionNote);
        ticket.setStatus(TicketStatus.RESOLVED);
    }

    @Override
    public void close(Ticket ticket) {
        throw new IllegalStateException("Cannot close in-progress ticket. Resolve first.");
    }

    @Override
    public void cancel(Ticket ticket) {
        ticket.addHistory(null, TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED, "Cancelled");
        ticket.setStatus(TicketStatus.CANCELLED);
    }

    @Override
    public TicketStatus getStatus() { return TicketStatus.IN_PROGRESS; }
}
