package com.asms.user.infrastructure.kafka;

import com.asms.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);
    private static final String TOPIC_USER_REGISTERED = "user.registered";
    private static final String TOPIC_USER_UPDATED = "user.updated";
    private static final String TOPIC_USER_DELETED = "user.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserRegistered(User user) {
        Map<String, Object> event = Map.of(
                "eventType", "USER_REGISTERED",
                "userId", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "societyId", user.getSocietyId()
        );
        send(TOPIC_USER_REGISTERED, user.getId(), event);
    }

    public void publishUserUpdated(User user) {
        Map<String, Object> event = Map.of(
                "eventType", "USER_UPDATED",
                "userId", user.getId(),
                "status", user.getStatus().name()
        );
        send(TOPIC_USER_UPDATED, user.getId(), event);
    }

    public void publishUserDeleted(String userId) {
        Map<String, Object> event = Map.of(
                "eventType", "USER_DELETED",
                "userId", userId
        );
        send(TOPIC_USER_DELETED, userId, event);
    }

    private void send(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic={} key={}: {}", topic, key, ex.getMessage());
            } else {
                log.debug("Event published to topic={} offset={}", topic,
                        result.getRecordMetadata().offset());
            }
        });
    }
}
