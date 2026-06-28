# Domain Model & Database Design – ASMS

---

## 1. Core Domain Entities

### 1.1 Property Hierarchy
```
Society (1) ──── (N) Tower
Tower   (1) ──── (N) Floor
Floor   (1) ──── (N) Unit
Unit    (1) ──── (0..1) Owner  (User)
Unit    (1) ──── (0..1) Tenant (User)
```

### 1.2 User Domain
```
User (abstract)
  ├── Owner
  ├── Tenant
  ├── Vendor
  ├── SecurityPersonnel
  ├── Employee
  └── DomesticHelp (Maid/Cook)
```

---

## 2. Java 21 Domain Records

```java
// User domain — immutable record
public record User(
    String id,
    String name,
    String email,
    String phone,
    UserRole role,
    String societyId,
    String unitId,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt
) {}

public enum UserRole {
    ADMIN, RWA_MEMBER, OWNER, TENANT, VENDOR,
    SECURITY, EMPLOYEE, DOMESTIC_HELP, VISITOR
}

public enum UserStatus { ACTIVE, INACTIVE, PENDING, SUSPENDED }

// Society hierarchy
public record Society(String id, String name, Address address, String rwaContactId) {}
public record Tower(String id, String societyId, String name, int totalFloors) {}
public record Floor(String id, String towerId, int floorNumber) {}
public record Unit(
    String id, String floorId, String unitNumber,
    UnitType type, String ownerId, String tenantId
) {}
public enum UnitType { STUDIO, ONE_BHK, TWO_BHK, THREE_BHK, PENTHOUSE, VILLA }

// Value object
public record Address(String line1, String line2, String city, String state, String pinCode) {}

// Amenity
public record Amenity(
    String id, String name, AmenityType type,
    String societyId, String vendorId,
    int capacity, BigDecimal ratePerHour, AmenityStatus status
) {}
public enum AmenityType { GYM, SWIMMING_POOL, CLUBHOUSE, TENNIS_COURT, PARKING, EXTERNAL_VENDOR }

// Booking
public record Booking(
    String id, String amenityId, String userId,
    LocalDate date, LocalTime startTime, LocalTime endTime,
    BookingStatus status, String paymentId
) {}
public enum BookingStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }

// Support Ticket
public record Ticket(
    String id, String title, String description,
    TicketCategory category, TicketPriority priority,
    String raisedBy, String assignedTo, String societyId,
    TicketStatus status, List<TicketHistory> history,
    Instant createdAt, Instant updatedAt
) {}
public enum TicketStatus  { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
public enum TicketPriority { P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW }
public enum TicketCategory { MAINTENANCE, PLUMBING, ELECTRICAL, SECURITY, BILLING, GENERAL }

public record TicketHistory(String changedBy, TicketStatus from, TicketStatus to, String note, Instant at) {}

// Visitor
public record VisitorRequest(
    String id, String visitorName, String visitorPhone, String purpose,
    String residentId, String unitId, String securityId,
    VisitorStatus status, Instant requestedAt, Instant expiresAt
) {}
public enum VisitorStatus { PENDING, APPROVED, REJECTED, EXPIRED, ENTERED }

// Payment
public record Payment(
    String id, String userId, BigDecimal amount, String currency,
    PaymentMethod method, String gatewayTransactionId,
    PaymentStatus status, PaymentReferenceType referenceType, String referenceId,
    Instant initiatedAt, Instant completedAt
) {}
public enum PaymentMethod   { UPI, DEBIT_CARD, CREDIT_CARD, NET_BANKING, WALLET }
public enum PaymentStatus   { INITIATED, PROCESSING, SUCCESS, FAILED, REFUNDED }
public enum PaymentReferenceType { MAINTENANCE, AMENITY_BOOKING, VENDOR_SERVICE, RENT }

// Invoice
public record Invoice(
    String id, String unitId, String userId,
    InvoiceType type, List<InvoiceLineItem> items,
    BigDecimal totalAmount, LocalDate dueDate, LocalDate paidDate,
    InvoiceStatus status
) {}
public enum InvoiceType   { RECURRING_MAINTENANCE, AD_HOC }
public enum InvoiceStatus { GENERATED, SENT, PAID, OVERDUE, WAIVED }

public record InvoiceLineItem(String description, BigDecimal amount, String serviceId) {}

// Workflow
public record WorkflowDefinition(
    String id, String name, String entityType,
    List<WorkflowStep> steps, boolean isActive
) {}
public record WorkflowStep(int order, String role, String action, boolean isParallel) {}
public record WorkflowInstance(
    String id, String definitionId, String entityId,
    WorkflowState state, int currentStep,
    List<WorkflowAudit> audit, Instant createdAt
) {}
public enum WorkflowState { DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, CANCELLED }
```

---

## 3. MongoDB Collections (per Microservice DB)

### user-service DB: `asms_users`

#### Collection: `users`
```json
{
  "_id": "usr_01J...",
  "name": "Raj Sharma",
  "email": "raj@example.com",
  "phone": "+91-9876543210",
  "role": "OWNER",
  "societyId": "soc_01J...",
  "unitId": "unit_01J...",
  "status": "ACTIVE",
  "keycloakId": "kc_01J...",
  "createdAt": { "$date": "2024-01-15T10:00:00Z" },
  "updatedAt": { "$date": "2024-01-15T10:00:00Z" }
}
```

#### Collection: `societies`
```json
{
  "_id": "soc_01J...",
  "name": "Green Residency",
  "address": {
    "line1": "123 MG Road",
    "city": "Bengaluru",
    "state": "Karnataka",
    "pinCode": "560001"
  },
  "rwaContactId": "usr_01J...",
  "createdAt": { "$date": "2024-01-01T00:00:00Z" }
}
```

#### Collection: `towers`
```json
{
  "_id": "tower_01J...",
  "societyId": "soc_01J...",
  "name": "Tower A",
  "totalFloors": 15,
  "liftCount": 2
}
```

#### Collection: `units`
```json
{
  "_id": "unit_01J...",
  "floorId": "floor_01J...",
  "unitNumber": "A-1201",
  "type": "TWO_BHK",
  "ownerId": "usr_01J...",
  "tenantId": null
}
```

---

### support-service DB: `asms_support`

#### Collection: `tickets`
```json
{
  "_id": "tkt_01J...",
  "title": "Water leakage in bathroom",
  "description": "Pipe burst under the sink.",
  "category": "PLUMBING",
  "priority": "P2_HIGH",
  "raisedBy": "usr_01J...",
  "assignedTo": "usr_02J...",
  "societyId": "soc_01J...",
  "status": "IN_PROGRESS",
  "history": [
    {
      "changedBy": "usr_admin",
      "from": "OPEN",
      "to": "IN_PROGRESS",
      "note": "Assigned to Ravi",
      "at": { "$date": "2024-01-16T09:00:00Z" }
    }
  ],
  "createdAt": { "$date": "2024-01-15T14:00:00Z" },
  "updatedAt": { "$date": "2024-01-16T09:00:00Z" }
}
```

---

### payment-service DB: `asms_payments`

#### Collection: `payments`
```json
{
  "_id": "pay_01J...",
  "userId": "usr_01J...",
  "amount": 5500.00,
  "currency": "INR",
  "method": "UPI",
  "gatewayTransactionId": "razorpay_txn_001",
  "status": "SUCCESS",
  "referenceType": "MAINTENANCE",
  "referenceId": "inv_01J...",
  "initiatedAt": { "$date": "2024-01-15T10:00:00Z" },
  "completedAt": { "$date": "2024-01-15T10:00:05Z" }
}
```

---

### billing-service DB: `asms_billing`

#### Collection: `invoices`
```json
{
  "_id": "inv_01J...",
  "unitId": "unit_01J...",
  "userId": "usr_01J...",
  "type": "RECURRING_MAINTENANCE",
  "items": [
    { "description": "Monthly Maintenance Fee", "amount": 5000.00 },
    { "description": "Water Charges", "amount": 500.00 }
  ],
  "totalAmount": 5500.00,
  "dueDate": { "$date": "2024-02-10T00:00:00Z" },
  "paidDate": null,
  "status": "SENT"
}
```

---

### visitor-service DB: `asms_visitors`

#### Collection: `visitor_requests`
```json
{
  "_id": "vis_01J...",
  "visitorName": "Amit Verma",
  "visitorPhone": "+91-9123456789",
  "purpose": "Personal Visit",
  "residentId": "usr_01J...",
  "unitId": "unit_01J...",
  "securityId": "usr_sec_01",
  "status": "APPROVED",
  "requestedAt": { "$date": "2024-01-15T17:00:00Z" },
  "expiresAt":   { "$date": "2024-01-15T17:05:00Z" }
}
```

---

### amenity-service DB: `asms_amenities`

#### Collection: `amenities`
```json
{
  "_id": "amen_01J...",
  "name": "Swimming Pool",
  "type": "SWIMMING_POOL",
  "societyId": "soc_01J...",
  "vendorId": null,
  "capacity": 30,
  "ratePerHour": 100.00,
  "status": "AVAILABLE",
  "operatingHours": { "open": "06:00", "close": "21:00" }
}
```

---

## 4. MongoDB Indexes

```javascript
// user-service
db.users.createIndex({ email: 1 }, { unique: true })
db.users.createIndex({ societyId: 1, role: 1 })
db.users.createIndex({ keycloakId: 1 }, { unique: true })
db.units.createIndex({ floorId: 1 })
db.units.createIndex({ ownerId: 1 })

// support-service
db.tickets.createIndex({ raisedBy: 1, status: 1 })
db.tickets.createIndex({ assignedTo: 1, status: 1 })
db.tickets.createIndex({ societyId: 1, createdAt: -1 })
db.tickets.createIndex({ status: 1, priority: 1 })

// payment-service
db.payments.createIndex({ userId: 1, status: 1 })
db.payments.createIndex({ referenceId: 1 })
db.payments.createIndex({ gatewayTransactionId: 1 }, { unique: true })

// billing-service
db.invoices.createIndex({ unitId: 1, status: 1 })
db.invoices.createIndex({ dueDate: 1, status: 1 })  // for overdue jobs
db.invoices.createIndex({ userId: 1, createdAt: -1 })

// visitor-service
db.visitor_requests.createIndex({ residentId: 1, status: 1 })
db.visitor_requests.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }) // TTL index

// amenity-service
db.bookings.createIndex({ amenityId: 1, date: 1, startTime: 1 })
db.bookings.createIndex({ userId: 1, status: 1 })
```

---

## 5. Entity Relationship Summary

```
Society ──1:N──► Tower ──1:N──► Floor ──1:N──► Unit
                                                  │
                              ┌────────────────────┤
                              │                    │
                           Owner (User)        Tenant (User)
                              │
                    Booking ──►──► Amenity
                    Invoice ──►──► Unit
                    Payment ──►──► Invoice
                    Ticket  ──►──► User (raisedBy, assignedTo)
                    VisitorRequest ──►──► User (resident, security)
                    WorkflowInstance ──►──► WorkflowDefinition
```
