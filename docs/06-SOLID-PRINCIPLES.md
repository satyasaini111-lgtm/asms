# SOLID Principles – ASMS
## Java 21 Implementations

---

## 1. Single Responsibility Principle (SRP)
> "There should never be more than one reason for a class to change."

### Applied: Separating TicketService from NotificationService

**Violation (before):**
```java
// BAD — one class doing too many things
public class TicketService {
    public Ticket createTicket(TicketRequest req) {
        Ticket ticket = save(req);
        // sends email — DIFFERENT responsibility
        emailSender.send(adminEmail, "New ticket: " + ticket.title());
        // sends SMS — yet ANOTHER responsibility
        smsSender.send(adminPhone, "New ticket raised");
        return ticket;
    }
}
```

**Fixed (SRP applied):**
```java
// GOOD — each class has ONE reason to change

// Responsibility 1: ticket business logic
@Service
public class TicketService {
    private final TicketRepository repository;
    private final DomainEventPublisher eventPublisher;

    public Ticket createTicket(TicketRequest req, String userId) {
        Ticket ticket = Ticket.builder()
            .title(req.title())
            .description(req.description())
            .category(req.category())
            .priority(req.priority())
            .raisedBy(userId)
            .status(TicketStatus.OPEN)
            .build();
        Ticket saved = repository.save(ticket);
        eventPublisher.publish(new TicketCreatedEvent(saved));  // event only
        return saved;
    }
}

// Responsibility 2: notification delivery (separate service/module)
@Service
public class NotificationService {
    @KafkaListener(topics = "asms.ticket.created")
    public void onTicketCreated(TicketCreatedEvent event) {
        notifyAdmin(event);
    }
    // only reason to change: notification channel/format changes
}
```

---

## 2. Open/Closed Principle (OCP)
> "Software entities should be open for extension, but closed for modification."

### Applied: Payment Gateway

**Violation (before):**
```java
// BAD — adding PayTM means modifying this class
public class PaymentService {
    public PaymentResult process(PaymentRequest req) {
        if (req.method() == PaymentMethod.UPI) { /* UPI logic */ }
        else if (req.method() == PaymentMethod.CARD) { /* card logic */ }
        // adding PAYTM requires modifying this switch — OCP violation
    }
}
```

**Fixed (OCP applied):**
```java
// GOOD — new payment method = new class, zero modification to existing

// Closed: interface never changes
public interface PaymentStrategy {
    PaymentResult execute(PaymentRequest request);
    PaymentMethod supportedMethod();
}

// Open: extend by adding new implementations
@Component public class UpiPaymentStrategy   implements PaymentStrategy { ... }
@Component public class CardPaymentStrategy  implements PaymentStrategy { ... }
@Component public class WalletPaymentStrategy implements PaymentStrategy { ... }
// Adding PayTM: just add @Component class PayTmPaymentStrategy — no existing code changes

// Context auto-discovers all strategies via Spring DI
@Service
public class PaymentService {
    private final Map<PaymentMethod, PaymentStrategy> strategies;
    public PaymentService(List<PaymentStrategy> all) {
        this.strategies = all.stream()
            .collect(Collectors.toMap(PaymentStrategy::supportedMethod, s -> s));
    }
}
```

---

## 3. Liskov Substitution Principle (LSP)
> "Subtypes must be substitutable for their base types."

### Applied: User Hierarchy

```java
// Base type
public abstract class BaseUser {
    protected final String id;
    protected final String name;
    protected final String email;

    public abstract boolean canBookAmenity();
    public abstract boolean canRaiseTicket();
    public abstract List<String> allowedActions();
}

// Subtype 1 — Owner
public class Owner extends BaseUser {
    @Override public boolean canBookAmenity()  { return true; }
    @Override public boolean canRaiseTicket()  { return true; }
    @Override public List<String> allowedActions() {
        return List.of("BOOK_AMENITY", "RAISE_TICKET", "VIEW_INVOICE", "PAY_MAINTENANCE");
    }
}

// Subtype 2 — Tenant (substitutable for BaseUser anywhere)
public class Tenant extends BaseUser {
    @Override public boolean canBookAmenity()  { return true; }
    @Override public boolean canRaiseTicket()  { return true; }
    @Override public List<String> allowedActions() {
        return List.of("BOOK_AMENITY", "RAISE_TICKET", "VIEW_INVOICE");
        // tenants CAN'T pay maintenance — owner does it, but they CAN do all else
    }
}

// Subtype 3 — Vendor
public class Vendor extends BaseUser {
    @Override public boolean canBookAmenity()  { return false; }
    @Override public boolean canRaiseTicket()  { return false; }
    @Override public List<String> allowedActions() {
        return List.of("MANAGE_SERVICES", "VIEW_BOOKINGS", "GENERATE_REPORTS");
    }
}

// LSP in action — any BaseUser subtype works here
public class AmenityBookingService {
    public Booking book(BaseUser user, String amenityId, TimeSlot slot) {
        if (!user.canBookAmenity()) throw new UnauthorizedException(user.id() + " cannot book amenities");
        // works correctly with Owner, Tenant — fails fast for Vendor without breaking anything
        return createBooking(user.id(), amenityId, slot);
    }
}
```

---

## 4. Interface Segregation Principle (ISP)
> "Clients should not be forced to depend upon interfaces they do not use."

### Applied: User Capability Interfaces

**Violation (before):**
```java
// BAD — fat interface; SecurityPersonnel forced to implement booking/payment methods
public interface UserActions {
    void bookAmenity(String amenityId);
    void raiseTicket(TicketRequest req);
    void payMaintenance(BigDecimal amount);
    void approveVisitor(String visitorId);    // only security does this
    void viewReports();                        // only admin does this
}
```

**Fixed (ISP applied):**
```java
// GOOD — lean, focused interfaces

public interface Bookable {
    Booking bookAmenity(String amenityId, TimeSlot slot);
    void cancelBooking(String bookingId);
}

public interface TicketRaiser {
    Ticket raiseTicket(TicketRequest req);
    void updateTicket(String ticketId, TicketUpdateRequest req);
}

public interface Payable {
    Payment payMaintenance(BigDecimal amount, PaymentMethod method);
    List<Invoice> viewInvoices();
}

public interface VisitorApprover {
    void approveVisitor(String visitorRequestId);
    void rejectVisitor(String visitorRequestId, String reason);
}

public interface ReportViewer {
    SocietyReport viewSocietyReport(String societyId, YearMonth month);
}

// Each role implements only what it needs
public class Owner  extends BaseUser implements Bookable, TicketRaiser, Payable { ... }
public class Tenant extends BaseUser implements Bookable, TicketRaiser { ... }
public class SecurityPersonnel extends BaseUser implements VisitorApprover { ... }
public class Admin  extends BaseUser implements ReportViewer, TicketRaiser { ... }
```

---

## 5. Dependency Inversion Principle (DIP)
> "Depend upon abstractions, not concretions."

### Applied: Repository + Messaging Abstractions

**Violation (before):**
```java
// BAD — TicketService directly depends on MongoDB and Kafka implementations
@Service
public class TicketService {
    @Autowired private MongoTemplate mongoTemplate;        // concrete MongoDB
    @Autowired private KafkaTemplate<String, Object> kafka; // concrete Kafka

    public Ticket createTicket(TicketRequest req) {
        Ticket ticket = new Ticket(...);
        mongoTemplate.save(ticket, "tickets");              // tied to MongoDB
        kafka.send("asms.ticket.created", ticket);         // tied to Kafka
        return ticket;
    }
}
```

**Fixed (DIP applied — Hexagonal/Ports & Adapters):**
```java
// Port (abstraction) — in domain layer
public interface TicketRepository {
    Ticket save(Ticket ticket);
    Optional<Ticket> findById(String id);
    List<Ticket> findByUserId(String userId, Pageable pageable);
}

public interface TicketEventPort {
    void publishCreated(Ticket ticket);
    void publishStatusChanged(Ticket ticket, TicketStatus previousStatus);
}

// High-level module depends on abstractions only
@Service
public class TicketService {
    private final TicketRepository repository;   // abstraction
    private final TicketEventPort eventPort;     // abstraction

    public TicketService(TicketRepository repository, TicketEventPort eventPort) {
        this.repository = repository;
        this.eventPort  = eventPort;
    }

    public Ticket createTicket(TicketRequest req, String userId) {
        Ticket ticket = buildTicket(req, userId);
        Ticket saved = repository.save(ticket);
        eventPort.publishCreated(saved);
        return saved;
    }
}

// Adapter (infrastructure layer) — implements the port
@Repository
public class MongoTicketRepository implements TicketRepository {
    private final MongoTemplate mongoTemplate;

    @Override
    public Ticket save(Ticket ticket) {
        return mongoTemplate.save(ticket, "tickets");
    }

    @Override
    public Optional<Ticket> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, Ticket.class));
    }
}

@Component
public class KafkaTicketEventAdapter implements TicketEventPort {
    private final KafkaTemplate<String, TicketCreatedEvent> kafkaTemplate;

    @Override
    public void publishCreated(Ticket ticket) {
        kafkaTemplate.send("asms.ticket.created", ticket.id(),
            TicketCreatedEvent.from(ticket));
    }
}

// Tests inject mock adapters — no real MongoDB/Kafka needed
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    @Mock TicketRepository mockRepo;
    @Mock TicketEventPort  mockEventPort;
    @InjectMocks TicketService ticketService;

    @Test
    void createTicket_publishesEvent() {
        // test purely against abstractions
    }
}
```

---

## SOLID Summary Table

| Principle | Class / Component | How Applied |
|---|---|---|
| **S** – Single Responsibility | `TicketService` vs `NotificationService` | Ticket logic and notification delivery are separate classes |
| **S** | `InvoiceGenerator` vs `InvoiceEmailer` | Generation and delivery are split |
| **O** – Open/Closed | `PaymentStrategy` interface | New payment method = new class, existing code untouched |
| **O** | `AbstractInvoiceGenerator` subclasses | New invoice type = new subclass |
| **L** – Liskov Substitution | `Owner extends BaseUser`, `Tenant extends BaseUser` | Both usable wherever `BaseUser` is expected |
| **I** – Interface Segregation | `Bookable`, `TicketRaiser`, `Payable`, `VisitorApprover` | Each role implements only relevant capabilities |
| **D** – Dependency Inversion | `TicketRepository` port, `MongoTicketRepository` adapter | Service depends on interface, not MongoDB class |
| **D** | `TicketEventPort` / `KafkaTicketEventAdapter` | Service doesn't know Kafka exists |
