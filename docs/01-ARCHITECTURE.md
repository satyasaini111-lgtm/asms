# Architecture Artifacts – ASMS

---

## 1. Business Capability Diagram (BCD)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ASMS – Business Capabilities                          │
├────────────────────────┬────────────────────────┬───────────────────────────┤
│  IDENTITY & ACCESS     │  PROPERTY MANAGEMENT   │  SERVICE MARKETPLACE      │
│  ─────────────────     │  ──────────────────     │  ───────────────────      │
│  • User Registration   │  • Society Setup       │  • Internal Amenities     │
│  • Role Management     │  • Tower/Floor/Unit    │  • Vendor Onboarding      │
│  • Authentication      │  • Unit Assignment     │  • Service Catalog        │
│  • Authorization       │  • Occupancy Tracking  │  • Amenity Booking        │
├────────────────────────┼────────────────────────┼───────────────────────────┤
│  OPERATIONS            │  FINANCIAL MANAGEMENT  │  COMMUNICATION            │
│  ──────────────        │  ───────────────────   │  ─────────────            │
│  • Workflow Engine     │  • Payment Processing  │  • Push Notifications     │
│  • Visitor Management  │  • Invoice Generation  │  • Apartment Forums       │
│  • Complaint / Tickets │  • Billing Reporting   │  • Chat & Help Bot        │
│  • Maintenance Sched.  │  • Overdue Tracking    │  • Announcements          │
├────────────────────────┴────────────────────────┴───────────────────────────┤
│  OBSERVABILITY & COMPLIANCE                                                  │
│  • Structured Logging  • Distributed Tracing  • Metrics & Alerting          │
│  • Audit Trail         • GDPR Compliance      • SLA Monitoring              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Business Context Diagram

```
                          ┌──────────────────────────────────────┐
                          │                                        │
    ┌──────────┐          │         ASMS Platform                 │          ┌───────────────┐
    │ Residents│◄────────►│  (Apartment Service Management System) │◄────────►│  B2B Vendors  │
    │ (Owners/ │          │                                        │          │ (Cab/Grocery/ │
    │ Tenants) │          │  ┌────────────┐  ┌────────────────┐  │          │  Electrician) │
    └──────────┘          │  │ User Mgmt  │  │ Amenity/Service│  │          └───────────────┘
                          │  └────────────┘  └────────────────┘  │
    ┌──────────┐          │  ┌────────────┐  ┌────────────────┐  │          ┌───────────────┐
    │   RWA    │◄────────►│  │  Billing   │  │    Visitor     │  │◄────────►│ Payment       │
    │Committee │          │  │  & Reports │  │   Management   │  │          │ Gateway       │
    └──────────┘          │  └────────────┘  └────────────────┘  │          │ (Razorpay/    │
                          │  ┌────────────┐  ┌────────────────┐  │          │  Stripe)      │
    ┌──────────┐          │  │  Support   │  │    Help Bot    │  │          └───────────────┘
    │ Security │◄────────►│  │  Tickets   │  │    (Q&A)       │  │
    │ Personnel│          │  └────────────┘  └────────────────┘  │          ┌───────────────┐
    └──────────┘          │                                        │◄────────►│ Notification  │
                          │  ┌─────────────────────────────────┐  │          │ Provider      │
    ┌──────────┐          │  │         API Gateway             │  │          │ (SMS/Email)   │
    │  Admin   │◄────────►│  │  (Auth · Rate Limit · Routing)  │  │          └───────────────┘
    └──────────┘          │  └─────────────────────────────────┘  │
                          └──────────────────────────────────────┘
```

---

## 3. System Context Diagram (C4 Level 1)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  External Actors                    ASMS System                  External Systems│
│                                                                                  │
│  [Resident Mobile App]──────►  ┌──────────────────────┐  ◄────[Payment Gateway] │
│  [Vendor Web Portal] ──────►  │                        │  ◄────[SMS Provider]   │
│  [Admin Dashboard]   ──────►  │     ASMS Platform      │  ◄────[Email Provider] │
│  [Security Tablet]   ──────►  │  (12 Microservices)    │  ◄────[Map/Location]   │
│  [RWA Portal]        ──────►  │                        │                        │
│                               └──────────────────────┘                         │
│                                         │                                        │
│                               ┌─────────┴──────────┐                           │
│                               │   AWS Cloud (EKS)   │                           │
│                               │  MongoDB · Kafka     │                           │
│                               │  Redis · Keycloak    │                           │
│                               └────────────────────┘                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Logical Application Architecture (Layered View)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                         │
│   REST Clients · Swagger UI · Postman · Mobile App (future)                  │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ HTTPS / JWT
┌───────────────────────────────▼─────────────────────────────────────────────┐
│                       API GATEWAY LAYER                                      │
│   Spring Cloud Gateway · JWT Validation (Keycloak) · Rate Limiting          │
│   Request Routing · CORS · Load Balancing · Circuit Breaker                  │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ HTTP (internal, mTLS via Istio)
┌───────────────────────────────▼─────────────────────────────────────────────┐
│                     SERVICE / API LAYER                                      │
│  ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐ │
│  │user-service│ │amenity-  │ │support-  │ │visitor-  │ │notification-   │ │
│  │            │ │service   │ │service   │ │service   │ │service         │ │
│  └────────────┘ └──────────┘ └──────────┘ └──────────┘ └────────────────┘ │
│  ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │payment-    │ │billing-  │ │workflow- │ │helpbot-  │  REST Controllers   │
│  │service     │ │service   │ │service   │ │service   │  OpenAPI 3.1 Docs   │
│  └────────────┘ └──────────┘ └──────────┘ └──────────┘                    │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────────────┐
│                    DOMAIN / BUSINESS LOGIC LAYER                             │
│   Use Case Services · Domain Entities (Java 21 Records) · Value Objects     │
│   GOF Design Patterns · SOLID Principles · Business Rules                    │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────────────┐
│                    INFRASTRUCTURE / ADAPTER LAYER                            │
│  ┌─────────────────┐  ┌───────────────────┐  ┌────────────────────────────┐│
│  │  MongoDB 7      │  │  Kafka 3.7 KRaft  │  │  Redis 7.2 Stack           ││
│  │  Repositories   │  │  Avro Producers/  │  │  Cache / Session            ││
│  │  (Spring Data)  │  │  Consumers        │  │                             ││
│  └─────────────────┘  └───────────────────┘  └────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Physical Deployment View (AWS)

```
┌─────────────────────────── AWS Region: ap-south-1 ─────────────────────────────┐
│                                                                                  │
│  ┌───────── Internet ──────────────────────────────────────────────────────┐   │
│  │                  Route 53 (DNS)                                          │   │
│  └──────────────────────┬───────────────────────────────────────────────────┘  │
│                         ▼                                                        │
│  ┌──────────── Public Subnets (AZ-a, AZ-b) ───────────────────────────────┐   │
│  │  Application Load Balancer (ALB)                                         │   │
│  │  Security Group: sg-alb (Inbound: 80/443 from 0.0.0.0/0)               │   │
│  └──────────────────────┬───────────────────────────────────────────────────┘  │
│                         ▼                                                        │
│  ┌──────────── Private Subnets – EKS Worker Nodes ────────────────────────┐   │
│  │  EKS Cluster: asms-eks (v1.30)                                           │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐    │   │
│  │  │  Namespace: asms-prod                                            │    │   │
│  │  │  ┌──────────────────────────────────────────────────────────┐  │    │   │
│  │  │  │  Istio Service Mesh (mTLS between all pods)              │  │    │   │
│  │  │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │    │   │
│  │  │  │  │api-gw    │ │user-svc  │ │amenity   │ │support   │  │  │    │   │
│  │  │  │  │(2 pods)  │ │(2 pods)  │ │(2 pods)  │ │(2 pods)  │  │  │    │   │
│  │  │  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │    │   │
│  │  │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │    │   │
│  │  │  │  │payment   │ │billing   │ │visitor   │ │notif.    │  │  │    │   │
│  │  │  │  │(2 pods)  │ │(2 pods)  │ │(2 pods)  │ │(2 pods)  │  │  │    │   │
│  │  │  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │    │   │
│  │  │  └──────────────────────────────────────────────────────────┘  │    │   │
│  │  │  Namespace: monitoring (Prometheus · Grafana · Loki · Tempo)   │    │   │
│  │  │  Namespace: argocd     (ArgoCD server + repo server)           │    │   │
│  │  │  Namespace: istio-system                                        │    │   │
│  │  └─────────────────────────────────────────────────────────────────┘    │   │
│  │  Worker Node 1 (t3.large, AZ-a)  |  Worker Node 2 (t3.large, AZ-b)     │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌──────────── Private Subnets – Data Layer ──────────────────────────────┐   │
│  │  ┌────────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │   │
│  │  │  Amazon DocumentDB │  │  Amazon MSK      │  │  ElastiCache Redis  │ │   │
│  │  │  (MongoDB 7 compat)│  │  Kafka 3 KRaft   │  │  7.2 Cluster Mode   │ │   │
│  │  │  3 instances       │  │  3 brokers       │  │  2 shards           │ │   │
│  │  │  SG: sg-docdb      │  │  SG: sg-msk      │  │  SG: sg-redis       │ │   │
│  │  └────────────────────┘  └─────────────────┘  └─────────────────────┘ │   │
│  │  ┌────────────────────┐  ┌─────────────────┐                            │   │
│  │  │  Keycloak (EC2)    │  │  Jenkins (EC2)   │                            │   │
│  │  │  t3.medium         │  │  t3.medium       │                            │   │
│  │  └────────────────────┘  └─────────────────┘                            │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌─── Supporting Services ──────────────────────────────────────────────────┐  │
│  │  ECR (12 repos) · Secrets Manager · S3 (Terraform state) · CloudWatch   │  │
│  │  IAM Roles (OIDC for GitHub Actions, EKS node roles)                    │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Application Architecture Diagram (Service Interactions)

```
Resident/Client
      │
      ▼
[API Gateway :8080]
  ├── JWT validation (Keycloak JWKS)
  ├── Rate limiting (Redis token bucket)
  └── Route by path prefix
        │
  ┌─────┴──────────────────────────────────────────────────┐
  │                                                          │
  ▼                                                          ▼
[user-service]                                        [visitor-service]
  ├── Keycloak sync                                      ├── POST entry-request
  └── MongoDB: users, societies                         ├── Kafka → visitor.entry.requested
                                                         └── PATCH approve → Kafka → visitor.approved
  ▼
[amenity-service]                                     [support-service]
  ├── Booking CRUD                                       ├── Ticket CRUD
  └── POST booking → [payment-service]                  ├── Kafka → ticket.*
                                                         └── ksqlDB: ticket_status table
  ▼
[payment-service]                                     [billing-service]
  ├── Strategy: UPI/Card/Wallet                         ├── @Scheduled monthly invoice job
  ├── Kafka → payment.completed                         ├── Kafka consumer: payment.completed
  └── Circuit Breaker → fallback                        └── MongoDB: invoices

  ▼
[workflow-service]                                    [helpbot-service]
  ├── State machine: DRAFT→APPROVED                     ├── Rule-based Q&A
  └── Chain of Responsibility: approvers                └── MongoDB: faq_rules, sessions

  ▼
[notification-service]  ◄── Kafka consumer (all topics)
  ├── Push (mocked)
  ├── Email (JavaMail mock)
  └── SMS (Twilio mock)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Cross-cutting:
  OpenTelemetry Agent → Grafana Alloy → Loki / Tempo / Prometheus → Grafana
  Istio Sidecar (Envoy) → mTLS between every pod pair
```

---

## 7. Use Case / Journey Diagrams

### 7.1 Resident Onboarding Journey
```
Resident → Register (user-service)
         → Workflow: Admin approval (workflow-service)
         → Notification: "Account approved" (notification-service)
         → Login via Keycloak → JWT issued
         → Book amenity (amenity-service)
         → Payment (payment-service)
         → Invoice generated (billing-service)
         → Notification: "Payment confirmed"
```

### 7.2 Visitor Entry Journey
```
Security logs visitor → visitor-service POST /entry-request
                     → Kafka: visitor.entry.requested (TTL 5 min)
                     → Resident receives push notification
                     → Resident PATCH /approve
                     → Kafka: visitor.approved
                     → Security receives real-time update
                     → Visitor allowed in
```

### 7.3 Support Ticket Journey
```
Resident POST /tickets → support-service
                      → Kafka: ticket.created
                      → Admin notified
                      → Support agent assigned (round-robin)
                      → Kafka: ticket.assigned → agent notified
                      → Agent updates → Kafka: ticket.status.changed
                      → Resident notified at each step
                      → CLOSED → Kafka: ticket.closed
                      → ksqlDB: ticket_status table updated in real-time
```

### 7.4 Monthly Billing Journey
```
@Scheduled (1st of month) → billing-service
                          → Fetch all active units
                          → Generate invoices (Template Method pattern)
                          → Kafka: invoice.generated
                          → Resident notified
                          → Due date + 7 days → overdue check
                          → Kafka: invoice.overdue → reminder sent
```
