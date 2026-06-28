# Messaging Design – Apache Kafka 3.7 (KRaft)
## Avro + Schema Registry + ksqlDB

---

## 1. Architecture

```
Microservice (Producer)
      │
      │  Avro serialized message
      ▼
Kafka Broker Cluster (3 brokers, KRaft — no ZooKeeper)
      │
      ├── Confluent Schema Registry (schema validation)
      │
      ├── ksqlDB (stream processing, materialized views)
      │
      └── Microservice (Consumer)
               │
               ▼
         notification-service / billing-service / etc.
```

---

## 2. Kafka Cluster Setup (KRaft Mode – No ZooKeeper)

```yaml
# docker-compose.yml
kafka-1:
  image: confluentinc/cp-kafka:7.6.1
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
    KAFKA_LOG_DIRS: /var/lib/kafka/data
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'
    CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'

schema-registry:
  image: confluentinc/cp-schema-registry:7.6.1
  environment:
    SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka-1:9092,kafka-2:9092,kafka-3:9092
    SCHEMA_REGISTRY_HOST_NAME: schema-registry
    SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081

ksqldb-server:
  image: confluentinc/cp-ksqldb-server:7.6.1
  environment:
    KSQL_BOOTSTRAP_SERVERS: kafka-1:9092
    KSQL_KSQL_SCHEMA_REGISTRY_URL: http://schema-registry:8081
```

---

## 3. Topic Design

| Topic Name | Partitions | Replication | Retention | Producer | Consumer |
|---|---|---|---|---|---|
| `asms.user.registered` | 3 | 3 | 7 days | user-service | notification-service |
| `asms.ticket.created` | 6 | 3 | 30 days | support-service | notification-service |
| `asms.ticket.assigned` | 6 | 3 | 30 days | support-service | notification-service |
| `asms.ticket.status.changed` | 6 | 3 | 30 days | support-service | notification-service |
| `asms.ticket.closed` | 6 | 3 | 30 days | support-service | notification-service |
| `asms.visitor.entry.requested` | 3 | 3 | 1 day | visitor-service | notification-service |
| `asms.visitor.approved` | 3 | 3 | 1 day | visitor-service | notification-service |
| `asms.visitor.rejected` | 3 | 3 | 1 day | visitor-service | notification-service |
| `asms.payment.completed` | 6 | 3 | 30 days | payment-service | billing-service |
| `asms.payment.failed` | 3 | 3 | 7 days | payment-service | notification-service |
| `asms.invoice.generated` | 3 | 3 | 30 days | billing-service | notification-service |
| `asms.invoice.overdue` | 3 | 3 | 7 days | billing-service | notification-service |
| `asms.notification.retry` | 3 | 3 | 3 days | notification-service | notification-service |
| `asms.notification.dlt` | 3 | 3 | 90 days | notification-service | manual review |

---

## 4. Avro Schemas

### TicketCreatedEvent
```json
{
  "namespace": "com.asms.events",
  "type": "record",
  "name": "TicketCreatedEvent",
  "doc": "Published when a new support ticket is raised",
  "fields": [
    {"name": "ticketId",   "type": "string"},
    {"name": "title",      "type": "string"},
    {"name": "raisedBy",   "type": "string",  "doc": "userId"},
    {"name": "assignedTo", "type": ["null", "string"], "default": null},
    {"name": "category",   "type": "string"},
    {"name": "priority",   "type": {
      "type": "enum", "name": "Priority",
      "symbols": ["P1_CRITICAL","P2_HIGH","P3_MEDIUM","P4_LOW"]
    }},
    {"name": "societyId",  "type": "string"},
    {"name": "createdAt",  "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

### TicketStatusChangedEvent
```json
{
  "namespace": "com.asms.events",
  "type": "record",
  "name": "TicketStatusChangedEvent",
  "fields": [
    {"name": "ticketId",       "type": "string"},
    {"name": "previousStatus", "type": "string"},
    {"name": "newStatus",      "type": "string"},
    {"name": "changedBy",      "type": "string"},
    {"name": "note",           "type": ["null", "string"], "default": null},
    {"name": "changedAt",      "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

### VisitorEntryRequestedEvent
```json
{
  "namespace": "com.asms.events",
  "type": "record",
  "name": "VisitorEntryRequestedEvent",
  "fields": [
    {"name": "requestId",    "type": "string"},
    {"name": "visitorName",  "type": "string"},
    {"name": "visitorPhone", "type": "string"},
    {"name": "purpose",      "type": "string"},
    {"name": "residentId",   "type": "string"},
    {"name": "unitId",       "type": "string"},
    {"name": "requestedAt",  "type": "long", "logicalType": "timestamp-millis"},
    {"name": "expiresAt",    "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

### PaymentCompletedEvent
```json
{
  "namespace": "com.asms.events",
  "type": "record",
  "name": "PaymentCompletedEvent",
  "fields": [
    {"name": "paymentId",      "type": "string"},
    {"name": "userId",         "type": "string"},
    {"name": "amount",         "type": "double"},
    {"name": "currency",       "type": "string"},
    {"name": "referenceType",  "type": "string"},
    {"name": "referenceId",    "type": "string"},
    {"name": "completedAt",    "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

---

## 5. Spring Boot Kafka Configuration

```yaml
# application.yml (support-service)
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      acks: all                  # wait for all replicas
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 1
        schema.registry.url: http://schema-registry:8081
    consumer:
      group-id: support-service-group
      auto-offset-reset: earliest
      enable-auto-commit: false   # manual ack
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        specific.avro.reader: true
        schema.registry.url: http://schema-registry:8081
    listener:
      ack-mode: MANUAL_IMMEDIATE
```

---

## 6. Producer Implementation

```java
@Component
public class TicketEventPublisher {

    private final KafkaTemplate<String, TicketCreatedEvent> kafkaTemplate;

    public void publishTicketCreated(Ticket ticket) {
        TicketCreatedEvent event = TicketCreatedEvent.newBuilder()
            .setTicketId(ticket.id())
            .setTitle(ticket.title())
            .setRaisedBy(ticket.raisedBy())
            .setCategory(ticket.category().name())
            .setPriority(Priority.valueOf(ticket.priority().name()))
            .setSocietyId(ticket.societyId())
            .setCreatedAt(ticket.createdAt().toEpochMilli())
            .build();

        kafkaTemplate.send("asms.ticket.created", ticket.id(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) log.error("Failed to publish ticket event: {}", ticket.id(), ex);
                else log.info("Published ticket.created for {}", ticket.id());
            });
    }
}
```

---

## 7. Consumer Implementation (notification-service)

```java
@Service
public class TicketNotificationConsumer {

    @KafkaListener(
        topics = "asms.ticket.created",
        groupId = "notification-group",
        containerFactory = "ticketCreatedListenerFactory"
    )
    public void onTicketCreated(TicketCreatedEvent event, Acknowledgment ack) {
        try {
            log.info("Processing ticket.created: {}", event.getTicketId());
            // Notify admin
            notificationService.send(NotificationRequest.builder()
                .recipient(adminLookup.findBySocietyId(event.getSocietyId()))
                .subject("New ticket raised")
                .body("Ticket #" + event.getTicketId() + ": " + event.getTitle())
                .build());
            ack.acknowledge();  // manual ack only on success
        } catch (Exception e) {
            log.error("Failed to process ticket.created — will retry", e);
            // do NOT ack — Kafka will redeliver
        }
    }

    @KafkaListener(topics = "asms.ticket.status.changed", groupId = "notification-group")
    public void onTicketStatusChanged(TicketStatusChangedEvent event, Acknowledgment ack) {
        notificationService.send(NotificationRequest.forResident(
            event.getTicketId(), event.getNewStatus(), event.getNote()));
        ack.acknowledge();
    }
}
```

---

## 8. ksqlDB Stream Processing

```sql
-- === TICKET LIFECYCLE STATE MANAGEMENT ===

-- Create stream from Kafka topic
CREATE STREAM ticket_status_stream (
  ticket_id VARCHAR KEY,
  previous_status VARCHAR,
  new_status VARCHAR,
  changed_by VARCHAR,
  note VARCHAR,
  changed_at BIGINT
) WITH (
  KAFKA_TOPIC = 'asms.ticket.status.changed',
  VALUE_FORMAT = 'AVRO',
  TIMESTAMP = 'changed_at'
);

-- Materialized table: latest status per ticket
CREATE TABLE ticket_current_status AS
  SELECT
    ticket_id,
    LATEST_BY_OFFSET(new_status) AS current_status,
    LATEST_BY_OFFSET(changed_by) AS last_changed_by,
    MAX(changed_at) AS last_updated_at
  FROM ticket_status_stream
  GROUP BY ticket_id
  EMIT CHANGES;

-- Query current status (REST API via ksqlDB)
-- GET http://ksqldb:8088/query
-- SELECT * FROM ticket_current_status WHERE ticket_id = 'tkt_01J';


-- === VISITOR ENTRY TTL MANAGEMENT ===

-- Create stream
CREATE STREAM visitor_entry_stream (
  request_id VARCHAR KEY,
  visitor_name VARCHAR,
  resident_id VARCHAR,
  unit_id VARCHAR,
  status VARCHAR,
  requested_at BIGINT,
  expires_at BIGINT
) WITH (
  KAFKA_TOPIC = 'asms.visitor.entry.requested',
  VALUE_FORMAT = 'AVRO',
  TIMESTAMP = 'requested_at'
);

-- Visitor current status table
CREATE TABLE visitor_current_status AS
  SELECT
    request_id,
    LATEST_BY_OFFSET(status) AS current_status,
    LATEST_BY_OFFSET(expires_at) AS expires_at
  FROM visitor_entry_stream
  GROUP BY request_id
  EMIT CHANGES;

-- Expired requests stream (for cleanup)
CREATE STREAM expired_visitor_requests AS
  SELECT *
  FROM visitor_entry_stream
  WHERE expires_at < UNIX_TIMESTAMP()
    AND LATEST_BY_OFFSET(status) = 'PENDING'
  EMIT CHANGES;


-- === PAYMENT ANALYTICS ===

CREATE STREAM payment_stream (
  payment_id VARCHAR KEY,
  user_id VARCHAR,
  amount DOUBLE,
  reference_type VARCHAR,
  completed_at BIGINT
) WITH (
  KAFKA_TOPIC = 'asms.payment.completed',
  VALUE_FORMAT = 'AVRO',
  TIMESTAMP = 'completed_at'
);

-- Daily revenue per society (via unit lookup enrichment)
CREATE TABLE daily_revenue AS
  SELECT
    reference_type,
    COUNT(*) AS transaction_count,
    SUM(amount) AS total_revenue
  FROM payment_stream
  WINDOW TUMBLING (SIZE 1 DAY)
  GROUP BY reference_type
  EMIT FINAL;
```

---

## 9. Dead Letter Topic Handling

```java
@Bean
public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dltTemplate) {
    DefaultErrorHandler handler = new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(dltTemplate,
            (record, ex) -> new TopicPartition("asms.notification.dlt", record.partition())),
        new FixedBackOff(1000L, 3)  // 3 retries, 1s apart
    );
    handler.addNotRetryableExceptions(SerializationException.class);
    return handler;
}
```

---

## 10. Messaging Exercise Summary

### Exercise 1 – Ticket Lifecycle Notifications
```
Resident → POST /tickets → support-service
         → Kafka: asms.ticket.created
              └── notification-service: Admin notified
         → Support assigned → Kafka: asms.ticket.assigned
              └── notification-service: Agent notified
         → Status update → Kafka: asms.ticket.status.changed
              └── notification-service: Resident notified
              └── ksqlDB: ticket_current_status table updated
         → Any user: GET /visitors/{id}/status → ksqlDB query → real-time status
```

### Exercise 2 – Visitor Approval with TTL
```
Security → POST /entry-request → visitor-service
         → Kafka: asms.visitor.entry.requested  (TTL: expiresAt = now + 5 min)
              └── notification-service: Resident push notification
         → Resident → PATCH /approve (within 5 min)
              → Kafka: asms.visitor.approved
              └── notification-service: Security notified → allow entry
         → If no response in 5 min:
              → ksqlDB: expired_visitor_requests stream fires
              → Status auto-set to EXPIRED
              → Security must call resident manually
```
