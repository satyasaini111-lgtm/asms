# ASMS — API Reference

## Base URL

```
http://k8s-asmsprod-asmsingr-7dc3e4d184-282727532.us-east-1.elb.amazonaws.com
```

All requests go through the **API Gateway** on port 8080. The gateway validates the JWT and injects `X-User-Id`, `X-User-Role`, and `X-Society-Id` headers before forwarding to downstream services.

---

## Authentication

All endpoints (except `POST /api/v1/users` and `GET /api/v1/helpbot/ask`) require a Bearer token.

```
Authorization: Bearer <access_token>
```

### Obtaining a Token (in-cluster)

Keycloak is not exposed externally. Run from inside the cluster:

```bash
kubectl exec -n asms-prod deployment/user-service -- sh -c \
  'wget -qO- --post-data \
  "client_id=asms-client&username=testuser&password=Test@1234&grant_type=password" \
  http://keycloak:8080/realms/asms/protocol/openid-connect/token' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
```

Token lifetime: **5 minutes**. Re-run when expired.

---

## Enum Reference

| Enum | Values |
|------|--------|
| `UserRole` | `ADMIN`, `RWA_MEMBER`, `OWNER`, `TENANT`, `VENDOR`, `SECURITY`, `SUPPORT_STAFF` |
| `UserStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| `AmenityType` | `SWIMMING_POOL`, `GYM`, `CLUBHOUSE`, `TENNIS_COURT`, `BADMINTON_COURT`, `BANQUET_HALL`, `LIBRARY`, `KIDS_PLAY_AREA`, `TERRACE`, `OTHER` |
| `BookingStatus` | `CONFIRMED`, `CANCELLED` |
| `TicketCategory` | `PLUMBING`, `ELECTRICAL`, `CARPENTRY`, `HOUSEKEEPING`, `SECURITY`, `LIFT`, `WATER_SUPPLY`, `INTERNET`, `PARKING`, `COMMON_AREA`, `OTHER` |
| `TicketPriority` | `P1_CRITICAL`, `P2_HIGH`, `P3_MEDIUM`, `P4_LOW` |
| `TicketStatus` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `PaymentMethod` | `UPI`, `CARD`, `NET_BANKING`, `WALLET`, `CASH` |
| `PaymentStatus` | `PENDING`, `SUCCESS`, `FAILED` |
| `InvoiceType` | `MONTHLY_MAINTENANCE`, `AMENITY_BOOKING`, `WATER_CHARGES`, `PARKING`, `PENALTY`, `AD_HOC` |
| `InvoiceStatus` | `PENDING`, `PAID`, `OVERDUE` |
| `WorkflowType` | `VENDOR_ONBOARDING`, `MAINTENANCE_REQUEST`, `LEASE_RENEWAL`, `CONSTRUCTION_PERMIT`, `FACILITY_BOOKING` |
| `ApprovalStatus` | `PENDING`, `APPROVED`, `REJECTED` |

---

## 1. User Service

Base path: `/api/v1/users`

### POST /api/v1/users
Register a new user. **No authentication required.**

**Request**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@asms.com",
  "phone": "9876543210",
  "role": "OWNER",
  "societyId": "SOCIETY-001",
  "unitId": "A-101"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `firstName` | string | yes | |
| `lastName` | string | yes | |
| `email` | string | yes | Must be valid email |
| `phone` | string | yes | 10–13 digits, optional `+` prefix |
| `role` | UserRole | yes | See enum table |
| `societyId` | string | yes | |
| `unitId` | string | no | |

**Response** `201 Created`
```json
{
  "id": "6686a3c0e2b4f12345678901",
  "email": "john.doe@asms.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "9876543210",
  "role": "OWNER",
  "status": "ACTIVE",
  "societyId": "SOCIETY-001",
  "unitId": "A-101",
  "createdAt": "2026-06-28T10:00:00Z"
}
```

---

### GET /api/v1/users/{id}
Get a user by ID. Requires any authenticated role.

**Response** `200 OK` — `UserResponse` (same shape as above)

---

### GET /api/v1/users
List users in a society. Requires `ADMIN` or `RWA_MEMBER`.

| Query Param | Required | Description |
|-------------|----------|-------------|
| `societyId` | yes | Filter by society |
| `role` | no | Filter by UserRole |
| `status` | no | Filter by UserStatus |
| `page` | no | Page number (default 0) |
| `size` | no | Page size (default 20) |

**Response** `200 OK` — Spring `Page<UserResponse>`

---

### PATCH /api/v1/users/{id}
Update a user. `ADMIN`/`RWA_MEMBER` can update any user; a user can update their own profile.

**Request**
```json
{
  "firstName": "John",
  "lastName": "Smith",
  "phone": "9876543211",
  "status": "ACTIVE",
  "unitId": "A-102"
}
```
All fields optional.

**Response** `200 OK` — `UserResponse`

---

### DELETE /api/v1/users/{id}
Delete a user (GDPR erasure). Requires `ADMIN`.

**Response** `204 No Content`

---

## 2. Amenity Service

Base path: `/api/v1/amenities` and `/api/v1/bookings`

### POST /api/v1/amenities
Create an amenity. Requires authentication.

**Request**
```json
{
  "societyId": "SOCIETY-001",
  "name": "Swimming Pool",
  "type": "SWIMMING_POOL",
  "capacity": 30,
  "hourlyRate": 200.00,
  "description": "Olympic-size pool on the terrace level",
  "operatingHours": "06:00-22:00"
}
```

| Field | Type | Required |
|-------|------|----------|
| `societyId` | string | yes |
| `name` | string | yes |
| `type` | AmenityType | yes |
| `capacity` | int | yes (≥ 1) |
| `hourlyRate` | decimal | no |
| `description` | string | no |
| `operatingHours` | string | no |

**Response** `201 Created`
```json
{
  "id": "668...",
  "societyId": "SOCIETY-001",
  "name": "Swimming Pool",
  "type": "SWIMMING_POOL",
  "capacity": 30,
  "hourlyRate": 200.00,
  "active": true,
  "description": "Olympic-size pool on the terrace level",
  "operatingHours": "06:00-22:00"
}
```

---

### GET /api/v1/amenities/{id}
Get amenity by ID.

**Response** `200 OK` — `AmenityResponse`

---

### GET /api/v1/amenities
List amenities for a society.

| Query Param | Required |
|-------------|----------|
| `societyId` | yes |
| `page` | no (default 0) |
| `size` | no (default 20) |

**Response** `200 OK` — `Page<AmenityResponse>`

---

### PUT /api/v1/amenities/{id}
Full update of an amenity. Same body as POST.

**Response** `200 OK` — `AmenityResponse`

---

### DELETE /api/v1/amenities/{id}
Soft-deactivate an amenity (`active: false`).

**Response** `204 No Content`

---

### POST /api/v1/bookings
Create a slot booking. Returns `409 Conflict` if slot overlaps.

**Request**
```json
{
  "amenityId": "668...",
  "userId": "668...",
  "societyId": "SOCIETY-001",
  "slotStart": "2026-07-01T08:00:00Z",
  "slotEnd":   "2026-07-01T10:00:00Z"
}
```

`slotStart` / `slotEnd` — ISO-8601 UTC (`Instant`).

**Response** `201 Created`
```json
{
  "id": "668...",
  "amenityId": "668...",
  "userId": "668...",
  "societyId": "SOCIETY-001",
  "slotStart": "2026-07-01T08:00:00Z",
  "slotEnd": "2026-07-01T10:00:00Z",
  "status": "CONFIRMED"
}
```

---

### GET /api/v1/bookings/{id}
Get booking by ID.

**Response** `200 OK` — `BookingResponse`

---

### GET /api/v1/bookings
List bookings for a user.

| Query Param | Required |
|-------------|----------|
| `userId` | yes |
| `page` | no |
| `size` | no |

**Response** `200 OK` — `Page<BookingResponse>`

---

### DELETE /api/v1/bookings/{id}
Cancel a booking.

**Response** `200 OK` — `BookingResponse` with `status: CANCELLED`

---

## 3. Support Service

Base path: `/api/v1/tickets`

### POST /api/v1/tickets
Raise a support ticket. Requires `OWNER` or `TENANT`.

**Request**
```json
{
  "title": "Water leakage in bathroom",
  "description": "Leakage from ceiling in master bedroom. Ongoing for 3 days.",
  "category": "PLUMBING",
  "priority": "P2_HIGH",
  "societyId": "SOCIETY-001"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `title` | string | yes | 5–200 chars |
| `description` | string | yes | max 2000 chars |
| `category` | TicketCategory | yes | |
| `priority` | TicketPriority | yes | |
| `societyId` | string | yes | |

**Response** `201 Created`
```json
{
  "id": "668...",
  "societyId": "SOCIETY-001",
  "raisedByUserId": "668...",
  "title": "Water leakage in bathroom",
  "description": "...",
  "category": "PLUMBING",
  "priority": "P2_HIGH",
  "status": "OPEN",
  "assignedToUserId": null,
  "resolutionNote": null,
  "createdAt": "2026-06-28T10:00:00Z",
  "updatedAt": "2026-06-28T10:00:00Z"
}
```

---

### GET /api/v1/tickets/{id}
Get ticket by ID. Requires `ADMIN`, `RWA_MEMBER`, `SUPPORT_STAFF`, `OWNER`, or `TENANT`.

**Response** `200 OK` — `TicketResponse`

---

### GET /api/v1/tickets
List tickets. Results filtered by role (residents see only their own).

| Query Param | Required |
|-------------|----------|
| `societyId` | no |
| `status` | no (TicketStatus) |
| `page` | no |
| `size` | no |

**Response** `200 OK` — `Page<TicketResponse>`

---

### PATCH /api/v1/tickets/{id}/assign
Assign ticket to support staff. Requires `ADMIN` or `RWA_MEMBER`.

| Query Param | Required |
|-------------|----------|
| `assignedToUserId` | yes |

**Response** `200 OK` — `TicketResponse` with `status: IN_PROGRESS`

---

### PATCH /api/v1/tickets/{id}/resolve
Resolve a ticket. Requires `ADMIN`, `RWA_MEMBER`, or `SUPPORT_STAFF`.

| Query Param | Required |
|-------------|----------|
| `resolutionNote` | yes |

**Response** `200 OK` — `TicketResponse` with `status: RESOLVED`

---

### PATCH /api/v1/tickets/{id}/close
Close a resolved ticket. Requires `ADMIN` or `RWA_MEMBER`. Ticket must be `RESOLVED`.

**Response** `200 OK` — `TicketResponse` with `status: CLOSED`

---

## 4. Visitor Service

Base path: `/api/v1/visitors`

**Lifecycle:** `PENDING → APPROVED / REJECTED → CHECKED_IN → CHECKED_OUT`

### POST /api/v1/visitors
Log a visitor entry request at the gate. Requires `SECURITY`.

**Request**
```json
{
  "visitorName": "Ravi Kumar",
  "visitorPhone": "9123456789",
  "vehicleNumber": "MH12AB1234",
  "purpose": "Delivery",
  "societyId": "SOCIETY-001",
  "residentUserId": "668..."
}
```

| Field | Type | Required |
|-------|------|----------|
| `visitorName` | string | yes |
| `visitorPhone` | string | yes (10–13 digits) |
| `vehicleNumber` | string | no |
| `purpose` | string | no |
| `societyId` | string | yes |
| `residentUserId` | string | yes |

**Response** `201 Created` — `VisitorRequest` entity

---

### PATCH /api/v1/visitors/{id}/approve
Resident approves visitor. Requires `OWNER` or `TENANT`.

**Response** `200 OK` — `VisitorRequest` with `status: APPROVED`

---

### PATCH /api/v1/visitors/{id}/reject
Resident rejects visitor. Requires `OWNER` or `TENANT`.

**Response** `200 OK` — `VisitorRequest` with `status: REJECTED`

---

### PATCH /api/v1/visitors/{id}/checkin
Security checks in an approved visitor. Requires `SECURITY`.

**Response** `200 OK` — `VisitorRequest` with `status: CHECKED_IN`

---

### PATCH /api/v1/visitors/{id}/checkout
Security checks out a visitor. Requires `SECURITY`.

**Response** `200 OK` — `VisitorRequest` with `status: CHECKED_OUT`

---

### GET /api/v1/visitors
List visitor requests for a society. Requires `ADMIN`, `RWA_MEMBER`, or `SECURITY`.

| Query Param | Required |
|-------------|----------|
| `societyId` | yes |
| `page` | no |
| `size` | no |

**Response** `200 OK` — `Page<VisitorRequest>`

---

## 5. Payment Service

Base path: `/api/v1/payments`

### POST /api/v1/payments
Initiate a payment. Requires `OWNER` or `TENANT`.

**Request**
```json
{
  "amount": 2500.00,
  "method": "UPI",
  "societyId": "SOCIETY-001",
  "referenceId": "668...",
  "referenceType": "INVOICE"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `amount` | decimal | yes | min 1.0 INR |
| `method` | PaymentMethod | yes | UPI, CARD, NET_BANKING, WALLET, CASH |
| `societyId` | string | yes | |
| `referenceId` | string | yes | Invoice ID or booking ID |
| `referenceType` | string | yes | e.g. `INVOICE`, `AMENITY_BOOKING` |

**Response** `201 Created`
```json
{
  "id": "668...",
  "userId": "668...",
  "societyId": "SOCIETY-001",
  "referenceId": "668...",
  "referenceType": "INVOICE",
  "amount": 2500.00,
  "currency": "INR",
  "method": "UPI",
  "status": "SUCCESS",
  "transactionId": "TXN-20260628-...",
  "createdAt": "2026-06-28T10:00:00Z"
}
```

---

### GET /api/v1/payments/{id}
Get payment by ID. Requires `ADMIN`, `RWA_MEMBER`, `OWNER`, or `TENANT`.

**Response** `200 OK` — `PaymentResponse`

---

### GET /api/v1/payments/my
Get payments for the authenticated user. Requires `OWNER` or `TENANT`.

| Query Param | Required |
|-------------|----------|
| `page` | no |
| `size` | no |

**Response** `200 OK` — `Page<PaymentResponse>`

---

## 6. Billing Service

Base path: `/api/v1/invoices`

### POST /api/v1/invoices/generate
Generate an invoice. Uses Template Method pattern to compute line items.

**Request**
```json
{
  "societyId": "SOCIETY-001",
  "userId": "668...",
  "unitId": "A-101",
  "type": "MONTHLY_MAINTENANCE",
  "billingPeriod": "2026-07"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `societyId` | string | yes | |
| `userId` | string | yes | |
| `unitId` | string | yes | |
| `type` | InvoiceType | yes | |
| `billingPeriod` | string | yes | Format `YYYY-MM` |

**Response** `201 Created`
```json
{
  "id": "668...",
  "societyId": "SOCIETY-001",
  "userId": "668...",
  "unitId": "A-101",
  "type": "MONTHLY_MAINTENANCE",
  "status": "PENDING",
  "totalAmount": 3500.00,
  "currency": "INR",
  "billingPeriod": "2026-07",
  "dueDate": "2026-07-10",
  "lineItems": [
    { "description": "Monthly Maintenance", "amount": 3000.00, "quantity": 1, "subtotal": 3000.00 },
    { "description": "Water Charges", "amount": 500.00, "quantity": 1, "subtotal": 500.00 }
  ],
  "createdAt": "2026-06-28T10:00:00Z"
}
```

---

### GET /api/v1/invoices/{id}
Get invoice by ID.

**Response** `200 OK` — `InvoiceResponse`

---

### GET /api/v1/invoices/user/{userId}
List invoices for a user.

| Query Param | Required |
|-------------|----------|
| `page` | no |
| `size` | no |

**Response** `200 OK` — `Page<InvoiceResponse>`

---

### GET /api/v1/invoices/society/{societyId}
List all invoices for a society.

| Query Param | Required |
|-------------|----------|
| `page` | no |
| `size` | no |

**Response** `200 OK` — `Page<InvoiceResponse>`

---

### PATCH /api/v1/invoices/{id}/pay
Mark an invoice as paid. Typically called after a successful payment.

**Response** `200 OK` — `InvoiceResponse` with `status: PAID`

---

## 7. Workflow Service

Base path: `/api/v1/workflows`

### POST /api/v1/workflows
Submit an approval request. Uses Chain of Responsibility: Security → RWA → Admin.

**Request**
```json
{
  "requesterId": "668...",
  "societyId": "SOCIETY-001",
  "workflowType": "MAINTENANCE_REQUEST",
  "documentType": "WORK_ORDER",
  "description": "Replacement of water pump in Block A",
  "metadata": {
    "estimatedCost": 15000,
    "vendorId": "VENDOR-001",
    "urgency": "HIGH"
  }
}
```

| Field | Type | Required |
|-------|------|----------|
| `requesterId` | string | yes |
| `societyId` | string | yes |
| `workflowType` | WorkflowType | yes |
| `documentType` | string | yes |
| `description` | string | no |
| `metadata` | map | no |

**Response** `201 Created`
```json
{
  "id": "668...",
  "requesterId": "668...",
  "societyId": "SOCIETY-001",
  "workflowType": "MAINTENANCE_REQUEST",
  "status": "PENDING",
  "documentType": "WORK_ORDER",
  "description": "Replacement of water pump in Block A",
  "metadata": { "estimatedCost": 15000 },
  "rejectionReason": null,
  "createdAt": "2026-06-28T10:00:00Z"
}
```

---

### GET /api/v1/workflows/{id}
Get approval by ID.

**Response** `200 OK` — `ApprovalResponse`

---

### GET /api/v1/workflows/requester/{requesterId}
List all workflows submitted by a requester.

| Query Param | Required |
|-------------|----------|
| `page` | no |
| `size` | no |

**Response** `200 OK` — `Page<ApprovalResponse>`

---

### GET /api/v1/workflows/society/{societyId}
List workflows for a society by status.

| Query Param | Required | Default |
|-------------|----------|---------|
| `status` | no | `PENDING` |
| `page` | no | 0 |
| `size` | no | 20 |

**Response** `200 OK` — `Page<ApprovalResponse>`

---

### POST /api/v1/workflows/{id}/process
Advance the workflow through the next approval handler. Call once per chain stage.

**Response** `200 OK` — `ApprovalResponse` (status becomes `APPROVED` after all handlers pass)

---

### POST /api/v1/workflows/{id}/reject
Reject the workflow at the current stage.

| Query Param | Required |
|-------------|----------|
| `reason` | yes |

**Response** `200 OK` — `ApprovalResponse` with `status: REJECTED`

---

## 8. HelpBot Service

Base path: `/api/v1/helpbot`

No authentication required.

### GET /api/v1/helpbot/ask

| Query Param | Required |
|-------------|----------|
| `query` | yes |

**Example**
```
GET /api/v1/helpbot/ask?query=How+do+I+raise+a+maintenance+request
```

**Response** `200 OK`
```json
{
  "query": "How do I raise a maintenance request?",
  "answer": "Go to the Support section and click 'New Ticket'. Select category PLUMBING/ELECTRICAL etc., set priority and submit."
}
```

---

### POST /api/v1/helpbot/ask

**Request**
```json
{
  "query": "What are the gym timings?"
}
```

**Response** `200 OK`
```json
{
  "query": "What are the gym timings?",
  "answer": "The gym is open from 05:30 to 23:00 daily. Booking required for peak hours."
}
```

---

## Error Responses

All services return a consistent error structure:

```json
{
  "timestamp": "2026-06-28T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: phone must be a valid phone number",
  "path": "/api/v1/users"
}
```

| HTTP Status | Meaning |
|-------------|---------|
| `400` | Validation failed (missing/invalid field) |
| `401` | Missing or expired JWT |
| `403` | JWT present but role not permitted |
| `404` | Resource not found |
| `409` | Conflict (e.g., duplicate email, overlapping booking slot) |
| `500` | Unexpected server error |
