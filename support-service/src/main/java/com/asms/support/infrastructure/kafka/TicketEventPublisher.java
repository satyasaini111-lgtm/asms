package com.asms.support.infrastructure.kafka;

import com.asms.support.domain.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);
    private static final String TOPIC_TICKET_CREATED = "ticket.created";
    private static final String TOPIC_TICKET_UPDATED = "ticket.status.changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TicketEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTicketCreated(Ticket ticket) {
        Map<String, Object> event = Map.of(
                "eventType", "TICKET_CREATED",
                "ticketId", ticket.getId(),
                "societyId", ticket.getSocietyId(),
                "raisedByUserId", ticket.getRaisedByUserId(),
                "category", ticket.getCategory().name(),
                "priority", ticket.getPriority().name(),
                "title", ticket.getTitle()
        );
        kafkaTemplate.send(TOPIC_TICKET_CREATED, ticket.getId(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish TICKET_CREATED: {}", ex.getMessage());
                });
    }

    public void publishStatusChanged(Ticket ticket, String previousStatus) {
        Map<String, Object> event = Map.of(
                "eventType", "TICKET_STATUS_CHANGED",
                "ticketId", ticket.getId(),
                "societyId", ticket.getSocietyId(),
                "previousStatus", previousStatus,
                "newStatus", ticket.getStatus().name()
        );
        kafkaTemplate.send(TOPIC_TICKET_UPDATED, ticket.getId(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish TICKET_STATUS_CHANGED: {}", ex.getMessage());
                });
    }
}
