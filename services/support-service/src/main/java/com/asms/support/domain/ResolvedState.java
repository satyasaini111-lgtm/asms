package com.asms.support.domain;

public class ResolvedState implements TicketState {

    @Override
    public void assign(Ticket ticket, String assignedTo) {
        throw new IllegalStateException("Cannot reassign a resolved ticket.");
    }

    @Override
    public void resolve(Ticket ticket, String resolutionNote) {
        throw new IllegalStateException("Ticket is already resolved.");
    }

    @Override
    public void close(Ticket ticket) {
        ticket.addHistory(null, TicketStatus.RESOLVED, TicketStatus.CLOSED, "Closed after resolution");
        ticket.setStatus(TicketStatus.CLOSED);
    }

    @Override
    public void cancel(Ticket ticket) {
        throw new IllegalStateException("Cannot cancel a resolved ticket.");
    }

    @Override
    public TicketStatus getStatus() { return TicketStatus.RESOLVED; }
}
