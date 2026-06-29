package com.asms.support.domain;

public class OpenState implements TicketState {

    @Override
    public void assign(Ticket ticket, String assignedTo) {
        ticket.setAssignedToUserId(assignedTo);
        ticket.addHistory(assignedTo, TicketStatus.OPEN, TicketStatus.IN_PROGRESS, "Assigned");
        ticket.setStatus(TicketStatus.IN_PROGRESS);
    }

    @Override
    public void resolve(Ticket ticket, String resolutionNote) {
        throw new IllegalStateException("Cannot resolve an unassigned ticket. Assign first.");
    }

    @Override
    public void close(Ticket ticket) {
        ticket.addHistory(null, TicketStatus.OPEN, TicketStatus.CLOSED, "Closed without resolution");
        ticket.setStatus(TicketStatus.CLOSED);
    }

    @Override
    public void cancel(Ticket ticket) {
        ticket.addHistory(null, TicketStatus.OPEN, TicketStatus.CANCELLED, "Cancelled");
        ticket.setStatus(TicketStatus.CANCELLED);
    }

    @Override
    public TicketStatus getStatus() { return TicketStatus.OPEN; }
}
