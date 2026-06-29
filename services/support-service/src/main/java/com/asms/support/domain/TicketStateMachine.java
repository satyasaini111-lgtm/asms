package com.asms.support.domain;

import org.springframework.stereotype.Component;

@Component
public class TicketStateMachine {

    public TicketState stateFor(Ticket ticket) {
        return switch (ticket.getStatus()) {
            case OPEN -> new OpenState();
            case IN_PROGRESS -> new InProgressState();
            case RESOLVED -> new ResolvedState();
            case CLOSED, CANCELLED -> throw new IllegalStateException(
                    "Ticket " + ticket.getId() + " is terminal: " + ticket.getStatus());
        };
    }
}
