# REST API Design – ASMS
## OpenAPI 3.1 Specification Reference

---

## Standards Applied
- RFC 9457 Problem Details for error responses
- Pagination via `?page=0&size=20&sort=createdAt,desc`
- Selective fields via `?fields=id,name,email`
- API versioning: `/api/v1/`, `/api/v2/`
- JWT Bearer token in `Authorization` header (all endpoints)

---

## Error Response Format (RFC 9457)
```json
{
  "type": "https://asms.io/errors/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Ticket with id 'tkt_01J' does not exist.",
  "instance": "/api/v1/tickets/tkt_01J",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

---

## 1. User Service APIs (`/api/v1`)

### Users
| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/users/register` | PUBLIC | Register a new user |
| `GET` | `/users/{id}` | ADMIN, SELF | Get user by ID |
| `PUT` | `/users/{id}` | ADMIN, SELF | Full update user profile |
| `PATCH` | `/users/{id}/status` | ADMIN | Activate / deactivate user |
| `DELETE` | `/users/{id}` | ADMIN | Soft delete user |
| `GET` | `/users` | ADMIN, RWA | List users (filter: role, societyId, status) |

### Societies
| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/societies` | ADMIN | Create society |
| `GET` | `/societies/{id}` | ANY | Get society details |
| `GET` | `/societies/{id}/towers` | ANY | List towers in society |
| `POST` | `/societies/{id}/towers` | ADMIN, RWA | Add tower |
| `POST` | `/towers/{id}/floors` | ADMIN, RWA | Add floor |
| `POST` | `/floors/{id}/units` | ADMIN, RWA | Add unit |
| `PATCH` | `/units/{id}/assign-owner` | ADMIN | Assign owner to unit |
| `PATCH` | `/units/{id}/assign-tenant` | ADMIN, OWNER | Assign tenant to unit |

### Vendors
| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/vendors/register` | PUBLIC | Vendor onboarding |
| `GET` | `/vendors/{id}` | ANY | Vendor profile |
| `GET` | `/vendors/{id}/services` | ANY | Vendor's service catalog |
| `PATCH` | `/vendors/{id}/approve` | ADMIN | Approve vendor |

**Sample Request – Register User:**
```json
POST /api/v1/users/register
Content-Type: application/json

{
  "name": "Raj Sharma",
  "email": "raj@example.com",
  "phone": "+91-9876543210",
  "role": "OWNER",
  "societyId": "soc_01J..."
}
```

**Sample Response:**
```json
HTTP 201 Created
{
  "id": "usr_01J...",
  "name": "Raj Sharma",
  "email": "raj@example.com",
  "role": "OWNER",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

---

## 2. Amenity Service APIs (`/api/v1`)

| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/amenities` | ADMIN, RWA | Create amenity |
| `GET` | `/amenities` | ANY | List amenities (filter: type, societyId) |
| `GET` | `/amenities/{id}` | ANY | Amenity details |
| `PUT` | `/amenities/{id}` | ADMIN, RWA | Update amenity |
| `DELETE` | `/amenities/{id}` | ADMIN | Deactivate amenity |
| `GET` | `/amenities/{id}/slots` | ANY | Available slots for a date |
| `POST` | `/amenities/{id}/bookings` | OWNER, TENANT | Book amenity slot |
| `GET` | `/bookings/{id}` | OWNER, TENANT, ADMIN | Booking details |
| `PATCH` | `/bookings/{id}/cancel` | OWNER, TENANT | Cancel booking |
| `GET` | `/bookings` | ADMIN, RWA | All bookings (filter: date, amenityId) |

**Sample – Book Amenity:**
```json
POST /api/v1/amenities/amen_01J/bookings
{
  "date": "2024-02-01",
  "startTime": "09:00",
  "endTime": "10:00"
}
```

---

## 3. Workflow Service APIs (`/api/v1`)

| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/workflows/definitions` | ADMIN | Create workflow definition |
| `GET` | `/workflows/definitions` | ADMIN, RWA | List workflow definitions |
| `POST` | `/workflows/instances` | ANY | Start a new workflow instance |
| `GET` | `/workflows/instances/{id}` | ANY | Get instance details |
| `PATCH` | `/workflows/instances/{id}/approve` | Approver Role | Approve current step |
| `PATCH` | `/workflows/instances/{id}/reject` | Approver Role | Reject with reason |
| `GET` | `/workflows/instances/{id}/history` | ANY | Full audit trail |

---

## 4. Support Service APIs (`/api/v1`)

| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/tickets` | OWNER, TENANT | Raise support ticket |
| `GET` | `/tickets/{id}` | ANY | Get ticket details |
| `PUT` | `/tickets/{id}` | OWNER, TENANT | Update ticket (title/description) |
| `PATCH` | `/tickets/{id}/assign` | ADMIN, RWA | Assign to support staff |
| `PATCH` | `/tickets/{id}/status` | SUPPORT, ADMIN | Update ticket status |
| `GET` | `/tickets/{id}/history` | ANY | Ticket status history |
| `GET` | `/tickets` | ANY | List tickets (filter: userId, societyId, status, priority) |

**Sample – Create Ticket:**
```json
POST /api/v1/tickets
{
  "title": "Water leakage in bathroom",
  "description": "Pipe burst under the sink since morning.",
  "category": "PLUMBING",
  "priority": "P2_HIGH"
}
```

**Sample – Update Status:**
```json
PATCH /api/v1/tickets/tkt_01J/status
{
  "status": "IN_PROGRESS",
  "note": "Plumber dispatched, will arrive by 3pm"
}
```

---

## 5. Payment Service APIs (`/api/v1`)

| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/payments/initiate` | OWNER, TENANT | Initiate payment |
| `GET` | `/payments/{id}` | SELF, ADMIN | Payment details |
| `POST` | `/payments/{id}/refund` | ADMIN | Initiate refund |
| `GET` | `/payments` | ADMIN | All payments (filter: userId, status, referenceType) |
| `POST` | `/payments/webhook` | GATEWAY | Payment gateway callback |

**Sample – Initiate Payment:**
```json
POST /api/v1/payments/initiate
{
  "amount": 5500.00,
  "currency": "INR",
  "method": "UPI",
  "referenceType": "MAINTENANCE",
  "referenceId": "inv_01J..."
}
```

---

## 6. Billing Service APIs (`/api/v1`)

| Method | Path | Auth Role | Description |
|---|---|---|---|
| `GET` | `/invoices/{id}` | SELF, ADMIN | Invoice details |
| `GET` | `/invoices` | ANY | List invoices (filter: unitId, status, month) |
| `GET` | `/invoices/{id}/download` | SELF, ADMIN | Download PDF invoice |
| `POST` | `/invoices/generate` | ADMIN, RWA | Manually trigger invoice generation |
| `GET` | `/reports/society/{id}` | ADMIN, RWA | Society-level billing report |
| `GET` | `/reports/unit/{id}` | SELF, ADMIN | Unit-level billing history |

---

## 7. Visitor Service APIs (`/api/v1`)

| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/visitors/entry-request` | SECURITY | Log new visitor entry |
| `GET` | `/visitors/{id}` | SECURITY, RESIDENT, ADMIN | Request details |
| `PATCH` | `/visitors/{id}/approve` | OWNER, TENANT | Approve visitor entry |
| `PATCH` | `/visitors/{id}/reject` | OWNER, TENANT | Reject visitor entry |
| `GET` | `/visitors` | SECURITY, ADMIN | List requests (filter: unitId, status, date) |
| `GET` | `/visitors/{id}/status` | SECURITY | Real-time status (via ksqlDB) |

**Sample – Log Visitor:**
```json
POST /api/v1/visitors/entry-request
{
  "visitorName": "Amit Verma",
  "visitorPhone": "+91-9123456789",
  "purpose": "Personal Visit",
  "unitId": "unit_01J..."
}
```

---

## 8. HelpBot Service APIs (`/api/v1`)

| Method | Path | Auth Role | Description |
|---|---|---|---|
| `POST` | `/helpbot/sessions` | ANY | Start chat session |
| `POST` | `/helpbot/sessions/{id}/messages` | ANY | Send message, get bot reply |
| `GET` | `/helpbot/sessions/{id}/history` | ANY | Session history |
| `GET` | `/helpbot/faqs` | ANY | List FAQs (filter: category) |

---

## 9. OpenAPI 3.1 Snippet (user-service)

```yaml
openapi: "3.1.0"
info:
  title: ASMS User Service API
  version: "1.0.0"
  description: Manages users, societies, towers, floors, and units.

servers:
  - url: http://localhost:8081
    description: Local development
  - url: https://api.asms.io
    description: Production (via API Gateway)

security:
  - bearerAuth: []

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    UserRegistrationRequest:
      type: object
      required: [name, email, phone, role]
      properties:
        name:  { type: string, minLength: 2, maxLength: 100 }
        email: { type: string, format: email }
        phone: { type: string, pattern: '^\+91-[0-9]{10}$' }
        role:  { type: string, enum: [OWNER, TENANT, VENDOR, SECURITY] }

    UserResponse:
      type: object
      properties:
        id:        { type: string }
        name:      { type: string }
        email:     { type: string }
        role:      { type: string }
        status:    { type: string }
        createdAt: { type: string, format: date-time }

    ProblemDetail:
      type: object
      properties:
        type:     { type: string, format: uri }
        title:    { type: string }
        status:   { type: integer }
        detail:   { type: string }
        instance: { type: string }
        traceId:  { type: string }

paths:
  /api/v1/users/register:
    post:
      summary: Register a new user
      operationId: registerUser
      tags: [Users]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UserRegistrationRequest'
      responses:
        '201':
          description: User registered
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '409':
          description: Email already registered
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '422':
          description: Validation error
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
```

---

## 10. Centralized Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://asms.io/errors/not-found"));
        detail.setProperty("traceId", MDC.get("traceId"));
        detail.setInstance(URI.create(req.getRequestURI()));
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Input validation failed");
        detail.setType(URI.create("https://asms.io/errors/validation-failed"));
        detail.setProperty("violations", errors);
        detail.setProperty("traceId", MDC.get("traceId"));
        return detail;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("https://asms.io/errors/duplicate-resource"));
        detail.setProperty("traceId", MDC.get("traceId"));
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setDetail("An unexpected error occurred. Please try again.");
        detail.setProperty("traceId", MDC.get("traceId"));
        return detail;
    }
}
```
