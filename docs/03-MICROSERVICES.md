# Microservice Architecture & Bounded Contexts – ASMS

---

## 1. Bounded Context Map

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                         ASMS Bounded Contexts                                  │
│                                                                                │
│  ┌─────────────────────┐      ┌─────────────────────┐                         │
│  │  IDENTITY CONTEXT   │      │  PROPERTY CONTEXT    │                         │
│  │                     │      │                      │                         │
│  │  user-service       │      │  (part of            │                         │
│  │  • User CRUD        │◄────►│   user-service)      │                         │
│  │  • Role assignment  │      │  • Society/Tower/    │                         │
│  │  • Keycloak sync    │      │    Floor/Unit CRUD   │                         │
│  └──────────┬──────────┘      └──────────────────────┘                         │
│             │                                                                   │
│  ┌──────────▼──────────┐      ┌─────────────────────┐                         │
│  │  SERVICE CONTEXT    │      │  OPERATIONS CONTEXT  │                         │
│  │                     │      │                      │                         │
│  │  amenity-service    │      │  workflow-service    │                         │
│  │  • Internal amen.   │      │  • Approval engine   │                         │
│  │  • Vendor services  │      │  • State machine     │                         │
│  │  • Booking          │      │                      │                         │
│  └──────────┬──────────┘      └──────────┬───────────┘                        │
│             │                             │                                     │
│  ┌──────────▼──────────┐      ┌──────────▼───────────┐                        │
│  │  FINANCIAL CONTEXT  │      │  SUPPORT CONTEXT     │                         │
│  │                     │      │                      │                         │
│  │  payment-service    │      │  support-service     │                         │
│  │  billing-service    │      │  • Ticket lifecycle  │                         │
│  │  • Invoicing        │      │  • Assignment        │                         │
│  │  • Recurring bills  │      │  • History/audit     │                         │
│  └──────────┬──────────┘      └──────────┬───────────┘                        │
│             │                             │                                     │
│  ┌──────────▼──────────────────▼──────────▼───────────┐                       │
│  │               COMMUNICATION CONTEXT                 │                       │
│  │                                                     │                       │
│  │  notification-service  helpbot-service              │                       │
│  │  visitor-service       (cross-cutting concern)      │                       │
│  └─────────────────────────────────────────────────────┘                       │
└───────────────────────────────────────────────────────────────────────────────┘
```

**Context Relationships:**
- **Partnership**: user-service ↔ amenity-service (shared User aggregate key)
- **Customer/Supplier**: billing-service (downstream) ← payment-service (upstream)
- **Conformist**: notification-service conforms to events from all upstream services
- **Anti-Corruption Layer**: payment-service wraps external payment gateway behind `PaymentPort` interface

---

## 2. Service Catalog

### 2.1 api-gateway
| Property | Value |
|---|---|
| Port | 8080 |
| Technology | Spring Cloud Gateway 4.x (reactive, non-blocking) |
| Responsibilities | JWT validation, routing, rate limiting, CORS, request logging |
| Key Routes | `/api/v1/users/**` → user-service, `/api/v1/amenities/**` → amenity-service, ... |
| Cross-cutting | OpenTelemetry trace propagation header injection |

**Rate Limiting config:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
            - name: CircuitBreaker
              args:
                name: userServiceCB
                fallbackUri: forward:/fallback
```

---

### 2.2 user-service
| Property | Value |
|---|---|
| Port | 8081 |
| DB | MongoDB: `asms_users` |
| Key Aggregates | User, Society, Tower, Floor, Unit |
| Publishes | `user.registered`, `user.deactivated` |
| Consumes | None |

**Package structure:**
```
com.asms.user
├── api
│   ├── UserController.java
│   ├── SocietyController.java
│   └── dto/  (request/response DTOs)
├── application
│   ├── UserService.java
│   └── SocietyService.java
├── domain
│   ├── User.java          (Java 21 record)
│   ├── Society.java
│   ├── Unit.java
│   └── enums/
├── infrastructure
│   ├── persistence/
│   │   ├── UserRepository.java
│   │   └── MongoUserRepository.java
│   ├── messaging/
│   │   └── UserEventPublisher.java
│   └── security/
│       └── KeycloakUserSyncService.java
└── config/
    └── SecurityConfig.java
```

---

### 2.3 amenity-service
| Property | Value |
|---|---|
| Port | 8082 |
| DB | MongoDB: `asms_amenities` |
| Key Aggregates | Amenity, Booking |
| Publishes | `booking.confirmed`, `booking.cancelled` |
| Consumes | `payment.completed` (update booking status) |
| Calls | payment-service (sync REST for payment initiation) |

---

### 2.4 workflow-service
| Property | Value |
|---|---|
| Port | 8083 |
| DB | MongoDB: `asms_workflows` |
| Key Aggregates | WorkflowDefinition, WorkflowInstance |
| Publishes | `workflow.approved`, `workflow.rejected` |
| Pattern | State Machine + Chain of Responsibility |

**State transitions:**
```
DRAFT → SUBMITTED → IN_REVIEW → APPROVED
                              → REJECTED
       → CANCELLED (any state)
```

---

### 2.5 support-service
| Property | Value |
|---|---|
| Port | 8084 |
| DB | MongoDB: `asms_support` + ksqlDB (ticket status view) |
| Key Aggregates | Ticket, TicketHistory |
| Publishes | `ticket.created`, `ticket.assigned`, `ticket.status.changed`, `ticket.closed` |
| Consumes | None |

**Assignment strategy:** Round-robin among active support staff in the society.

---

### 2.6 payment-service
| Property | Value |
|---|---|
| Port | 8085 |
| DB | MongoDB: `asms_payments` |
| Key Aggregates | Payment, PaymentMethod |
| Publishes | `payment.completed`, `payment.failed`, `payment.refunded` |
| Pattern | Strategy (UPI/Card/Wallet adapters), Circuit Breaker |

---

### 2.7 billing-service
| Property | Value |
|---|---|
| Port | 8086 |
| DB | MongoDB: `asms_billing` |
| Key Aggregates | Invoice, InvoiceLineItem |
| Publishes | `invoice.generated`, `invoice.overdue` |
| Consumes | `payment.completed` |
| Scheduled | `@Scheduled(cron = "0 0 1 1 * *")` — 1st of every month |

---

### 2.8 visitor-service
| Property | Value |
|---|---|
| Port | 8087 |
| DB | MongoDB: `asms_visitors` + ksqlDB (visitor status stream) |
| Key Aggregates | VisitorRequest |
| Publishes | `visitor.entry.requested`, `visitor.approved`, `visitor.rejected` |
| TTL | MongoDB TTL index on `expiresAt` (auto-expire after 5 min) |

---

### 2.9 notification-service
| Property | Value |
|---|---|
| Port | 8088 |
| DB | None (stateless consumer) |
| Consumes | ALL event topics |
| Channels | Push (FCM mock), Email (JavaMail mock), SMS (Twilio mock) |
| Pattern | Observer (Kafka consumers), Abstract Factory (channel factory) |

---

### 2.10 helpbot-service
| Property | Value |
|---|---|
| Port | 8089 |
| DB | MongoDB: `asms_helpbot` |
| Key Aggregates | ChatSession, ChatMessage, FaqRule |
| Pattern | Rule-based matching, Chain of Responsibility for FAQ resolution |

---

## 3. Inter-Service Communication

### Synchronous (REST via Spring WebClient)
```java
// payment-service called from amenity-service
@Service
public class AmenityPaymentClient {
    private final WebClient webClient;

    public Mono<PaymentResponse> initiatePayment(PaymentRequest request) {
        return webClient.post()
            .uri("/api/v1/payments/initiate")
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is5xxServerError,
                res -> Mono.error(new PaymentServiceException("Payment service down")))
            .bodyToMono(PaymentResponse.class)
            .timeout(Duration.ofSeconds(3));
    }
}
```

### Asynchronous (Kafka with Avro)
```java
// support-service produces ticket event
@Service
public class TicketEventPublisher {
    private final KafkaTemplate<String, TicketCreatedEvent> kafkaTemplate;

    public void publishTicketCreated(Ticket ticket) {
        TicketCreatedEvent event = TicketCreatedEvent.newBuilder()
            .setTicketId(ticket.id())
            .setRaisedBy(ticket.raisedBy())
            .setCategory(ticket.category().name())
            .setPriority(Priority.valueOf(ticket.priority().name()))
            .setSocietyId(ticket.societyId())
            .setCreatedAt(ticket.createdAt().toEpochMilli())
            .build();
        kafkaTemplate.send("asms.ticket.created", ticket.id(), event);
    }
}
```

---

## 4. Service Aggregates (DDD)

| Service | Aggregate Root | Child Entities | Value Objects |
|---|---|---|---|
| user-service | User | - | Address, ContactInfo |
| user-service | Society | Tower, Floor, Unit | Address |
| amenity-service | Amenity | - | OperatingHours, PriceInfo |
| amenity-service | Booking | - | TimeSlot |
| workflow-service | WorkflowDefinition | WorkflowStep | - |
| workflow-service | WorkflowInstance | WorkflowAudit | - |
| support-service | Ticket | TicketHistory | - |
| payment-service | Payment | - | Money (amount + currency) |
| billing-service | Invoice | InvoiceLineItem | Money |
| visitor-service | VisitorRequest | - | TimeWindow |

---

## 5. Fault Tolerance Configuration

### Circuit Breaker (payment-service from amenity-service)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        failure-rate-threshold: 80
        sliding-window-size: 100
        wait-duration-in-open-state: 30s
        permitted-calls-in-half-open-state: 5
```

### Retry (notification-service Kafka consumer)
```yaml
spring:
  kafka:
    consumer:
      properties:
        max.poll.interval.ms: 300000
retry:
  topic: asms.notification.retry
  dlt-topic: asms.notification.dlt   # dead letter topic after 3 retries
```

### Istio VirtualService (mesh-level)
```yaml
spec:
  http:
    - retries:
        attempts: 3
        perTryTimeout: 2s
        retryOn: "gateway-error,connect-failure"
      timeout: 10s
```
