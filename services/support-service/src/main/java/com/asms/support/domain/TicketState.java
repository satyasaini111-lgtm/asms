package com.asms.support.domain;

// State Pattern — each state defines allowed transitions
public interface TicketState {
    void assign(Ticket ticket, String assignedTo);
    void resolve(Ticket ticket, String resolutionNote);
    void close(Ticket ticket);
    void cancel(Ticket ticket);
    TicketStatus getStatus();
}
