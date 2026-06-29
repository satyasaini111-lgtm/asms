# ASMS — Architecture Document

## 1. Overview

The **Apartment Service Management System (ASMS)** is a cloud-native microservices platform that digitises the day-to-day operations of a residential housing society. It covers eight use cases — user management, amenity booking, support tickets, visitor management, payments, billing, approval workflows, and an AI-assisted help bot — each implemented as an independent deployable service.

```
                          ┌─────────────────────────────────────────────────┐
                          │              AWS EKS 1.36 Auto Mode              │
                          │                                                   │
  Client / Postman        │   ┌─────────────────────────────────────────┐   │
  ─────────────────►  ALB─┼──►│            API Gateway  :8080            │   │
                          │   │  • JWT validation (Keycloak JWKS)        │   │
                          │   │  • UserContextFilter → X-User-Id/Role    │   │
                          │   │  • Route: http://service:port            │   │
                          │   └──────────────┬──────────────────────────┘   │
                          │                  │  (HTTP, Kubernetes DNS)       │
                          │     ┌────────────┼───────────────┐              │
                          │     ▼            ▼               ▼              │
                          │  user-svc   amenity-svc    support-svc           │
                          │  visitor    payment-svc    billing-svc            │
                          │  workflow   helpbot-svc                           │
                          │                                                   │
                          │   ┌──────────┐  ┌───────┐  ┌────────────────┐  │
                          │   │ MongoDB  │  │ Redis │  │ Kafka (KRaft)  │  │
                          │   │   7.0    │  │  7.2  │  │     7.6.1      │  │
                          │   └──────────┘  └───────┘  └────────────────┘  │
                          │                                                   │
                          │   ┌──────────┐                                   │
                          │   │ Keycloak │  (identity provider, internal)    │
                          │   │  24.0.5  │                                   │
                          │   └──────────┘                                   │
                          └─────────────────────────────────────────────────┘
```

---

## 2. Services

| # | Service | Port | Responsibility |
|---|---------|------|---------------|
| 1 | **api-gateway** | 8080 | JWT validation, routing, header propagation |
| 2 | **user-service** | 8081 | Registration, profile, role management |
| 3 | **amenity-service** | 8083 | Amenity CRUD, slot booking, conflict detection |
| 4 | **support-service** | 8084 | Ticket lifecycle (State Machine pattern) |
| 5 | **visitor-service** | 8085 | Visitor entry request and gate-control workflow |
| 6 | **payment-service** | 8086 | Payment initiation (Strategy pattern) |
| 7 | **billing-service** | 8087 | Invoice generation (Template Method pattern) |
| 8 | **workflow-service** | 8088 | Multi-stage approvals (Chain of Responsibility) |
| 9 | **helpbot-service** | 8090 | Rule-based FAQ chatbot |
| — | **Keycloak** | 8080 | OAuth2 / OIDC identity provider |
| — | **MongoDB** | 27017 | Primary datastore (per-service collections) |
| — | **Redis** | 6379 | Gateway caching / rate-limit state |
| — | **Kafka** | 9092 | Async domain event bus (KRaft mode) |

---

## 3. Design Patterns

### 3.1 State Machine — Support Ticket (`support-service`)
Ticket states `OPEN → IN_PROGRESS → RESOLVED → CLOSED` are enforced by a `TicketStateMachine` using the State design pattern. Each state (`OpenState`, `InProgressState`, `ResolvedState`) encapsulates the allowed transitions and rejects illegal moves.

```
OPEN ──assign──► IN_PROGRESS ──resolve──► RESOLVED ──close──► CLOSED
```

### 3.2 Strategy — Payment Processing (`payment-service`)
Each `PaymentMethod` (UPI, CARD, NET_BANKING, WALLET, CASH) is an independent `PaymentStrategy` implementation. `PaymentServiceImpl` selects the strategy at runtime, keeping payment logic isolated and extensible.

```
PaymentService
    └── PaymentStrategy (interface)
            ├── UpiPaymentStrategy
            ├── CardPaymentStrategy
            └── NetBankingPaymentStrategy
```

### 3.3 Chain of Responsibility — Approvals (`workflow-service`)
Approval requests travel through a configurable handler chain: `SecurityApprovalHandler → RwaApprovalHandler → AdminApprovalHandler`. Each handler decides to approve and forward, or short-circuit with a rejection. The chain is wired in `ApprovalChainConfig`.

```
Request ──► Security ──► RWA ──► Admin ──► APPROVED
                 └──► REJECTED (at any stage)
```

### 3.4 Template Method — Invoice Generation (`billing-service`)
`AbstractInvoiceGenerator` defines the generation skeleton (create invoice, populate line items, compute totals, persist). Concrete subclasses `RecurringMaintenanceInvoiceGenerator` and `AdHocInvoiceGenerator` provide the line-item specifics.

### 3.5 Observer / Event-Driven (Kafka)
Domain events are published to Kafka after state changes. Downstream services consume them for side-effects (notifications, audit logs) without tight coupling.

---

## 4. Security Architecture

### 4.1 Authentication Flow

```
Client
  │
  ├─1─► Keycloak (http://keycloak:8080/realms/asms)
  │       POST /protocol/openid-connect/token
  │       → access_token (JWT, RS256, iss=http://keycloak:8080/realms/asms)
  │
  └─2─► API Gateway  (Authorization: Bearer <token>)
          │  Validates JWT signature via JWKS endpoint
          │  Extracts realm_access.roles → picks first ASMS-specific role
          │  Mutates request: adds X-User-Id, X-User-Role, X-Society-Id
          │
          └─3─► Downstream service
                  GatewayHeaderAuthFilter reads X-User-Id + X-User-Role
                  Builds SecurityContext with ROLE_<role> authority
                  @PreAuthorize annotations enforce per-endpoint access
```

### 4.2 Roles and Permissions

| Role | Key Permissions |
|------|----------------|
| `ADMIN` | Full access to all endpoints |
| `RWA_MEMBER` | List/manage users, assign/close tickets, approve workflows |
| `OWNER` | Raise tickets, approve visitors, make payments, book amenities |
| `TENANT` | Same as OWNER |
| `SECURITY` | Log/check-in/check-out visitors |
| `SUPPORT_STAFF` | Resolve support tickets |
| `VENDOR` | Vendor-specific operations |

### 4.3 Header Propagation

The `UserContextFilter` (api-gateway) injects three headers that downstream services rely on:

| Header | Source | Used by |
|--------|--------|---------|
| `X-User-Id` | `jwt.sub` | TicketController, VisitorController, PaymentController |
| `X-User-Role` | `realm_access.roles` (first ASMS role) | GatewayHeaderAuthFilter, TicketController |
| `X-Society-Id` | JWT claim `society_id` | GatewayHeaderAuthFilter MDC |

---

## 5. Infrastructure

### 5.1 AWS Stack

| Layer | Resource |
|-------|---------|
| Compute | EKS 1.36 Auto Mode (Fargate-based, serverless pods) |
| Load Balancer | ALB via EKS built-in LBC (`eks.amazonaws.com/alb`) |
| Container Registry | Amazon ECR (one repo per service under `asms/`) |
| Networking | VPC with public + private subnets across 2 AZs |
| IAM | Cluster role with `AmazonEKSComputePolicy`, `AmazonEKSLoadBalancingPolicy`, `AmazonEKSNetworkingPolicy` + `sts:TagSession` for EKS networking chain |

### 5.2 Kubernetes Layout

```
Namespace: asms-prod
├── Deployments (1 replica each, HPA up to 8)
│   ├── api-gateway
│   ├── user-service
│   ├── amenity-service
│   ├── support-service
│   ├── visitor-service
│   ├── payment-service
│   ├── billing-service
│   ├── workflow-service
│   ├── helpbot-service
│   ├── keycloak
│   └── redis
├── StatefulSets (maxUnavailable: 1)
│   ├── mongodb
│   └── kafka
├── IngressClass: alb  (controller: eks.amazonaws.com/alb)
├── Ingress: asms-ingress  (spec.ingressClassName: alb)
├── ConfigMap: asms-common-config
└── Secret: asms-secrets
```

### 5.3 Probe Settings (Java services on Fargate)

Java 21 / Spring Boot 3.x cold-start on Fargate takes 80–100 s. Probes are tuned accordingly:

| Probe | initialDelaySeconds | periodSeconds | failureThreshold |
|-------|--------------------:|:-------------:|:----------------:|
| Readiness | 90 | 10 | 6 |
| Liveness | 120 | 15 | 3 |

**Why:** Fargate has no persistent filesystem or warm JVM. A liveness probe that fires before startup completes will kill the pod and create a crash loop.

### 5.4 EKS Auto Mode Known Constraints

| Constraint | Workaround |
|-----------|-----------|
| No kube-proxy pre-installed | CoreDNS EKS addon added |
| LBC ignores `kubernetes.io/ingress.class` annotation | Use `spec.ingressClassName: alb` + explicit `IngressClass` resource |
| StatefulSet `maxUnavailable` stripped | Set `maxUnavailable: 1` explicitly in `updateStrategy` |
| Networking chain role needs session tags | Add `sts:TagSession` to cluster IAM role trust policy |

---

## 6. CI/CD Pipeline

Jenkins pipeline (`Jenkinsfile`) with 4 stages:

```
Build & Test  ──►  Push to ECR  ──►  Deploy to EKS  ──►  Smoke Test
(mvn package)      (docker push)      (kubectl apply)     (curl ALB)
```

1. **Build & Test** — `mvn clean package -DskipTests` for all 9 modules; Docker images built with ECR base mirror to avoid Docker Hub rate limits.
2. **Push to ECR** — Tags image `<ecr-registry>/asms/<service>:<git-sha>`.
3. **Deploy to EKS** — Applies ConfigMap, Secrets, IngressClass, Ingress, infra (MongoDB/Redis/Kafka/Keycloak) and all service deployments. Uses `kubectl rollout status --timeout=900s`.
4. **Smoke Test** — Waits for ALB DNS, curls `/actuator/health` on api-gateway and `POST /api/v1/users` to verify end-to-end routing.

---

## 7. Data Flow Example — Resident Raises Support Ticket

```
1. Resident (OWNER role) obtains JWT from Keycloak
2. POST /api/v1/tickets  → ALB → api-gateway
3. api-gateway: validates JWT, extracts X-User-Id + X-User-Role=OWNER
4. support-service: GatewayHeaderAuthFilter sets SecurityContext
5. @PreAuthorize("hasAnyRole('OWNER','TENANT')") — passes
6. TicketService.createTicket() → persists to MongoDB (status: OPEN)
7. TicketEventPublisher publishes TicketCreatedEvent to Kafka
8. notification-service (consumer) sends push notification to RWA members
9. RWA member calls PATCH /api/v1/tickets/{id}/assign → status: IN_PROGRESS
10. Support staff calls PATCH /api/v1/tickets/{id}/resolve → status: RESOLVED
11. RWA member calls PATCH /api/v1/tickets/{id}/close → status: CLOSED
```

---

## 8. Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 21 (Virtual Threads) |
| Framework | Spring Boot | 3.3.11 |
| API Gateway | Spring Cloud Gateway | 2023.0.3 |
| Security | Spring Security + OAuth2 Resource Server | — |
| Persistence | Spring Data MongoDB | — |
| Messaging | Spring Kafka | — |
| Cache | Spring Data Redis | — |
| Build | Apache Maven (multi-module) | 3.x |
| Containers | Docker | — |
| Orchestration | Kubernetes / EKS 1.36 Auto Mode | — |
| Identity | Keycloak | 24.0.5 |
| Database | MongoDB | 7.0 |
| Broker | Confluent Kafka (KRaft) | 7.6.1 |
| Cache store | Redis | 7.2 Alpine |
| Observability | Micrometer + Prometheus annotations | — |
| IaC | Terraform | — |
