package com.asms.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EventNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EventNotificationListener.class);

    private final List<NotificationChannel> channels;

    public EventNotificationListener(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    @KafkaListener(topics = "ticket.created", groupId = "notification-service")
    public void onTicketCreated(Map<String, Object> event, Acknowledgment ack) {
        try {
            String userId = (String) event.get("raisedByUserId");
            String ticketId = (String) event.get("ticketId");
            String title = (String) event.get("title");
            notify(userId, "Ticket Created: " + ticketId,
                    "Your support ticket '" + title + "' has been raised successfully.");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process ticket.created event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "visitor.entry.requested", groupId = "notification-service")
    public void onVisitorEntryRequested(Map<String, Object> event, Acknowledgment ack) {
        try {
            String residentUserId = (String) event.get("residentUserId");
            String visitorName = (String) event.get("visitorName");
            notify(residentUserId, "Visitor Entry Request",
                    visitorName + " is at the gate. Approve or reject within 5 minutes.");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process visitor.entry.requested event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "invoice.issued", groupId = "notification-service")
    public void onInvoiceIssued(Map<String, Object> event, Acknowledgment ack) {
        try {
            String userId = (String) event.get("userId");
            Object amount = event.get("amount");
            String dueDate = (String) event.get("dueDate");
            notify(userId, "Invoice Issued",
                    "Your invoice for INR " + amount + " is due on " + dueDate + ".");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process invoice.issued event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service")
    public void onPaymentCompleted(Map<String, Object> event, Acknowledgment ack) {
        try {
            String userId = (String) event.get("userId");
            Object amount = event.get("amount");
            notify(userId, "Payment Confirmed", "Payment of INR " + amount + " received. Thank you!");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment.completed event: {}", e.getMessage());
        }
    }

    private void notify(String userId, String subject, String body) {
        channels.forEach(channel -> {
            try {
                channel.send(userId, subject, body);
            } catch (Exception e) {
                log.warn("Channel {} failed for userId={}: {}", channel.channelType(), userId, e.getMessage());
            }
        });
    }
}
