# GOF Design Patterns – ASMS
## Java 21 Implementations

---

## Pattern Application Map

| Pattern | Type | Applied In | Class/Interface |
|---|---|---|---|
| Singleton | Creational | Kafka Producer, Config | `KafkaProducerConfig`, `AppConfig` |
| Factory Method | Creational | Payment gateway selection | `PaymentGatewayFactory` |
| Abstract Factory | Creational | Notification channels | `NotificationChannelFactory` |
| Builder | Creational | Invoice, Ticket, User creation | `Invoice.Builder`, `Ticket.Builder` |
| Prototype | Creational | Workflow template cloning | `WorkflowDefinition.clone()` |
| Adapter | Structural | Payment gateway wrappers | `RazorpayAdapter`, `StripeAdapter` |
| Facade | Structural | Single entry-point for services | `ApartmentServiceFacade` |
| Decorator | Structural | Caching + logging wrappers | `CachingAmenityService` |
| Proxy | Structural | Redis cache proxy | `CachedUserRepository` |
| Composite | Structural | Society hierarchy traversal | `PropertyNode` |
| Strategy | Behavioral | Payment methods | `UpiPaymentStrategy`, `CardPaymentStrategy` |
| Observer | Behavioral | Kafka event publishing | `TicketEventPublisher` |
| State | Behavioral | Ticket and workflow lifecycle | `TicketStateMachine` |
| Chain of Responsibility | Behavioral | Workflow approvals, FAQ bot | `ApprovalChain` |
| Command | Behavioral | Booking with undo support | `BookingCommand`, `CancelBookingCommand` |
| Template Method | Behavioral | Invoice generation variants | `AbstractInvoiceGenerator` |
| Iterator | Behavioral | Paginated MongoDB results | `PagedEntityIterator` |

---

## 1. Strategy Pattern – Payment Methods

```java
// Strategy interface
public interface PaymentStrategy {
    PaymentResult execute(PaymentRequest request);
    PaymentMethod supportedMethod();
}

// Concrete strategies
@Component
public class UpiPaymentStrategy implements PaymentStrategy {
    @Override
    public PaymentResult execute(PaymentRequest request) {
        // call UPI gateway adapter
        return PaymentResult.success(UUID.randomUUID().toString());
    }
    @Override
    public PaymentMethod supportedMethod() { return PaymentMethod.UPI; }
}

@Component
public class CardPaymentStrategy implements PaymentStrategy {
    @Override
    public PaymentResult execute(PaymentRequest request) {
        return PaymentResult.success(UUID.randomUUID().toString());
    }
    @Override
    public PaymentMethod supportedMethod() { return PaymentMethod.CREDIT_CARD; }
}

// Context — selects strategy at runtime
@Service
public class PaymentService {
    private final Map<PaymentMethod, PaymentStrategy> strategies;

    public PaymentService(List<PaymentStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(PaymentStrategy::supportedMethod, Function.identity()));
    }

    public PaymentResult processPayment(PaymentRequest request) {
        PaymentStrategy strategy = strategies.get(request.method());
        if (strategy == null) throw new UnsupportedPaymentMethodException(request.method());
        return strategy.execute(request);
    }
}
```

---

## 2. State Pattern – Ticket Lifecycle

```java
// State interface
public interface TicketState {
    TicketState assign(Ticket ticket, String assignee);
    TicketState startProgress(Ticket ticket);
    TicketState resolve(Ticket ticket, String resolution);
    TicketState close(Ticket ticket);
    TicketStatus status();
}

// Concrete states
public class OpenState implements TicketState {
    @Override
    public TicketState assign(Ticket ticket, String assignee) {
        ticket.setAssignedTo(assignee);
        return new InProgressState();
    }
    @Override
    public TicketState startProgress(Ticket ticket) { throw new InvalidTransitionException("Must assign first"); }
    @Override
    public TicketState resolve(Ticket t, String r)  { throw new InvalidTransitionException("Must be IN_PROGRESS"); }
    @Override
    public TicketState close(Ticket t)              { throw new InvalidTransitionException("Must be RESOLVED"); }
    @Override
    public TicketStatus status() { return TicketStatus.OPEN; }
}

public class InProgressState implements TicketState {
    @Override
    public TicketState assign(Ticket t, String a)      { return this; } // re-assign allowed
    @Override
    public TicketState startProgress(Ticket t)         { return this; }
    @Override
    public TicketState resolve(Ticket t, String note)  { return new ResolvedState(); }
    @Override
    public TicketState close(Ticket t)                 { throw new InvalidTransitionException("Resolve first"); }
    @Override
    public TicketStatus status() { return TicketStatus.IN_PROGRESS; }
}

public class ResolvedState implements TicketState {
    @Override public TicketState assign(Ticket t, String a)     { throw new InvalidTransitionException(); }
    @Override public TicketState startProgress(Ticket t)        { throw new InvalidTransitionException(); }
    @Override public TicketState resolve(Ticket t, String note) { return this; }
    @Override public TicketState close(Ticket t)                { return new ClosedState(); }
    @Override public TicketStatus status() { return TicketStatus.RESOLVED; }
}

// Context
public class TicketStateMachine {
    private TicketState currentState;

    public TicketStateMachine(TicketStatus initial) {
        this.currentState = stateFor(initial);
    }

    public void transition(TicketTransition transition, Ticket ticket, Object payload) {
        currentState = switch (transition) {
            case ASSIGN   -> currentState.assign(ticket, (String) payload);
            case PROGRESS -> currentState.startProgress(ticket);
            case RESOLVE  -> currentState.resolve(ticket, (String) payload);
            case CLOSE    -> currentState.close(ticket);
        };
    }

    private TicketState stateFor(TicketStatus status) {
        return switch (status) {
            case OPEN        -> new OpenState();
            case IN_PROGRESS -> new InProgressState();
            case RESOLVED    -> new ResolvedState();
            case CLOSED      -> new ClosedState();
        };
    }
}
```

---

## 3. Chain of Responsibility – Workflow Approvals

```java
// Handler interface
public abstract class ApprovalHandler {
    protected ApprovalHandler next;

    public ApprovalHandler setNext(ApprovalHandler next) {
        this.next = next;
        return next;
    }

    public abstract ApprovalResult handle(ApprovalRequest request);
}

// Concrete handlers
public class SecurityApprovalHandler extends ApprovalHandler {
    @Override
    public ApprovalResult handle(ApprovalRequest request) {
        if (request.requiresSecurityApproval()) {
            // process security approval
            return ApprovalResult.approved("Security approved");
        }
        return next != null ? next.handle(request) : ApprovalResult.skipped();
    }
}

public class RwaApprovalHandler extends ApprovalHandler {
    @Override
    public ApprovalResult handle(ApprovalRequest request) {
        if (request.requiresRwaApproval()) {
            return ApprovalResult.approved("RWA approved");
        }
        return next != null ? next.handle(request) : ApprovalResult.skipped();
    }
}

public class AdminApprovalHandler extends ApprovalHandler {
    @Override
    public ApprovalResult handle(ApprovalRequest request) {
        // Admin is final approver — always handles
        return ApprovalResult.approved("Admin final approval");
    }
}

// Building the chain
@Configuration
public class WorkflowChainConfig {
    @Bean
    public ApprovalHandler approvalChain() {
        ApprovalHandler security = new SecurityApprovalHandler();
        ApprovalHandler rwa      = new RwaApprovalHandler();
        ApprovalHandler admin    = new AdminApprovalHandler();
        security.setNext(rwa).setNext(admin);
        return security;
    }
}
```

---

## 4. Observer Pattern – Kafka Event Publishing

```java
// Event interface
public interface DomainEvent {
    String aggregateId();
    Instant occurredAt();
}

// Publisher (Observable)
@Component
public class DomainEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Map<Class<? extends DomainEvent>, String> topicMap;

    public void publish(DomainEvent event) {
        String topic = topicMap.get(event.getClass());
        if (topic == null) throw new UnregisteredEventException(event.getClass());
        kafkaTemplate.send(topic, event.aggregateId(), event);
    }
}

// Subscriber (notification-service)
@Service
public class TicketNotificationListener {
    @KafkaListener(topics = "asms.ticket.created", groupId = "notification-group")
    public void onTicketCreated(TicketCreatedEvent event) {
        // Notify admin
        notificationService.send(NotificationRequest.forAdmin(event));
    }

    @KafkaListener(topics = "asms.ticket.assigned", groupId = "notification-group")
    public void onTicketAssigned(TicketAssignedEvent event) {
        // Notify assigned agent
        notificationService.send(NotificationRequest.forAgent(event));
    }
}
```

---

## 5. Abstract Factory – Notification Channels

```java
// Abstract factory
public interface NotificationChannelFactory {
    PushNotifier createPushNotifier();
    EmailNotifier createEmailNotifier();
    SmsNotifier createSmsNotifier();
}

// Concrete factory — production
@Component
@Profile("prod")
public class ProductionNotificationFactory implements NotificationChannelFactory {
    @Override public PushNotifier createPushNotifier()  { return new FcmPushNotifier(); }
    @Override public EmailNotifier createEmailNotifier() { return new SendGridEmailNotifier(); }
    @Override public SmsNotifier createSmsNotifier()    { return new TwilioSmsNotifier(); }
}

// Concrete factory — test/mock
@Component
@Profile("test")
public class MockNotificationFactory implements NotificationChannelFactory {
    @Override public PushNotifier createPushNotifier()  { return req -> log.info("MOCK PUSH: {}", req); }
    @Override public EmailNotifier createEmailNotifier() { return req -> log.info("MOCK EMAIL: {}", req); }
    @Override public SmsNotifier createSmsNotifier()    { return req -> log.info("MOCK SMS: {}", req); }
}
```

---

## 6. Template Method – Invoice Generation

```java
// Abstract template
public abstract class AbstractInvoiceGenerator {

    // Template method — defines the skeleton
    public final Invoice generate(String unitId, YearMonth month) {
        List<InvoiceLineItem> items = collectLineItems(unitId, month);
        BigDecimal total = calculateTotal(items);
        Invoice invoice = buildInvoice(unitId, items, total, month);
        postProcess(invoice);
        return invoice;
    }

    // Steps — subclasses override
    protected abstract List<InvoiceLineItem> collectLineItems(String unitId, YearMonth month);

    protected BigDecimal calculateTotal(List<InvoiceLineItem> items) {
        return items.stream()
            .map(InvoiceLineItem::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    protected abstract Invoice buildInvoice(String unitId, List<InvoiceLineItem> items,
                                             BigDecimal total, YearMonth month);

    protected void postProcess(Invoice invoice) { /* hook — optional override */ }
}

// Concrete: recurring monthly maintenance
@Service
public class RecurringInvoiceGenerator extends AbstractInvoiceGenerator {
    @Override
    protected List<InvoiceLineItem> collectLineItems(String unitId, YearMonth month) {
        return List.of(
            new InvoiceLineItem("Monthly Maintenance", new BigDecimal("5000")),
            new InvoiceLineItem("Water Charges",       new BigDecimal("500"))
        );
    }

    @Override
    protected Invoice buildInvoice(String unitId, List<InvoiceLineItem> items,
                                    BigDecimal total, YearMonth month) {
        return new Invoice(UUID.randomUUID().toString(), unitId, null,
            InvoiceType.RECURRING_MAINTENANCE, items, total,
            month.atEndOfMonth().plusDays(10), null, InvoiceStatus.GENERATED);
    }
}

// Concrete: ad-hoc (amenity usage, vendor service)
@Service
public class AdHocInvoiceGenerator extends AbstractInvoiceGenerator {
    @Override
    protected List<InvoiceLineItem> collectLineItems(String unitId, YearMonth month) {
        return adHocUsageRepository.findByUnitAndMonth(unitId, month).stream()
            .map(u -> new InvoiceLineItem(u.description(), u.amount()))
            .toList();
    }

    @Override
    protected Invoice buildInvoice(String unitId, List<InvoiceLineItem> items,
                                    BigDecimal total, YearMonth month) {
        return new Invoice(UUID.randomUUID().toString(), unitId, null,
            InvoiceType.AD_HOC, items, total,
            LocalDate.now().plusDays(7), null, InvoiceStatus.GENERATED);
    }
}
```

---

## 7. Decorator Pattern – Caching Service Wrapper

```java
// Primary interface
public interface AmenityService {
    Amenity findById(String id);
    List<Amenity> findBySociety(String societyId);
}

// Core implementation
@Service
@Primary
public class AmenityServiceImpl implements AmenityService { ... }

// Caching decorator
@Service
public class CachingAmenityService implements AmenityService {
    private final AmenityService delegate;
    private final RedisTemplate<String, Amenity> redis;

    public CachingAmenityService(AmenityServiceImpl delegate, RedisTemplate<String, Amenity> redis) {
        this.delegate = delegate;
        this.redis = redis;
    }

    @Override
    public Amenity findById(String id) {
        String key = "amenity:" + id;
        Amenity cached = redis.opsForValue().get(key);
        if (cached != null) return cached;
        Amenity amenity = delegate.findById(id);
        redis.opsForValue().set(key, amenity, Duration.ofMinutes(30));
        return amenity;
    }

    @Override
    public List<Amenity> findBySociety(String societyId) {
        return delegate.findBySociety(societyId); // no cache for list queries
    }
}
```

---

## 8. Composite Pattern – Property Hierarchy

```java
// Component interface
public interface PropertyNode {
    String getId();
    String getName();
    List<PropertyNode> children();
    int totalUnits();
}

// Leaf
public record UnitNode(String id, String unitNumber) implements PropertyNode {
    @Override public List<PropertyNode> children() { return List.of(); }
    @Override public int totalUnits()               { return 1; }
    @Override public String getName()               { return unitNumber; }
}

// Composite
public class FloorNode implements PropertyNode {
    private final String id;
    private final int floorNumber;
    private final List<PropertyNode> units;

    @Override public List<PropertyNode> children() { return units; }
    @Override public int totalUnits() { return units.stream().mapToInt(PropertyNode::totalUnits).sum(); }
    @Override public String getName() { return "Floor " + floorNumber; }
}

public class TowerNode implements PropertyNode {
    private final String id;
    private final String name;
    private final List<PropertyNode> floors;

    @Override public List<PropertyNode> children() { return floors; }
    @Override public int totalUnits() { return floors.stream().mapToInt(PropertyNode::totalUnits).sum(); }
    @Override public String getName() { return name; }
}

// Usage: traverse society to count total units
society.children()                // towers
    .stream()
    .mapToInt(PropertyNode::totalUnits)
    .sum();
```

---

## 9. Builder Pattern – Invoice (Java 21 Record with Builder)

```java
public record Invoice(
    String id, String unitId, String userId, InvoiceType type,
    List<InvoiceLineItem> items, BigDecimal totalAmount,
    LocalDate dueDate, LocalDate paidDate, InvoiceStatus status
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id       = UUID.randomUUID().toString();
        private String unitId, userId;
        private InvoiceType type;
        private List<InvoiceLineItem> items = new ArrayList<>();
        private BigDecimal totalAmount;
        private LocalDate dueDate;
        private LocalDate paidDate;
        private InvoiceStatus status = InvoiceStatus.GENERATED;

        public Builder unitId(String v)    { unitId = v; return this; }
        public Builder userId(String v)    { userId = v; return this; }
        public Builder type(InvoiceType v) { type = v;   return this; }
        public Builder addItem(InvoiceLineItem item) { items.add(item); return this; }
        public Builder dueDate(LocalDate v){ dueDate = v; return this; }

        public Invoice build() {
            totalAmount = items.stream().map(InvoiceLineItem::amount)
                              .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Invoice(id, unitId, userId, type, List.copyOf(items),
                               totalAmount, dueDate, paidDate, status);
        }
    }
}
```
