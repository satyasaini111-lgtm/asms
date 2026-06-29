# Assumptions – ASMS
## All Assumptions Made for the Integrated Exercise

---

## 1. Domain / Business Assumptions

| # | Assumption | Rationale |
|---|---|---|
| 1 | Each apartment complex is modeled as a **Society** containing multiple **Towers** → **Floors** → **Units** | Industry-standard property hierarchy |
| 2 | A **Tenant** is someone who rents a unit; an **Owner** may or may not reside in their unit | Owners can rent out; tenant records are optional |
| 3 | A unit can have at most **one active owner** and **one active tenant** at a time | Simplification — no co-ownership split modeled |
| 4 | **Maintenance charges** are flat-rate per unit per month, configurable by Admin/RWA | Variable-rate billing is an extension, not in scope |
| 5 | The **RWA committee** is elected periodically; for this exercise, RWA roles are manually assigned by Admin | No election workflow implemented |
| 6 | **Domestic help** (maid/cook) requires Security approval + Owner consent before access is granted | Standard gated community practice |
| 7 | **Visitor entry requests** expire after **5 minutes** if the resident does not approve or reject | Balances security with convenience |
| 8 | A **support ticket** must be raised by a registered user (Owner/Tenant) — anonymous tickets not supported | User accountability required |
| 9 | **Amenity booking** slots are 1-hour minimum; double-booking the same slot is not permitted | Simple calendar model |
| 10 | **Payment refunds** are manual (Admin-initiated) — no automated refund gateway integration | Payment gateway is mocked |

---

## 2. Technical Assumptions

| # | Assumption | Rationale |
|---|---|---|
| 11 | **No UI is implemented** — exercise requires service layer only; Swagger UI serves as API explorer | Explicitly stated in exercise instructions |
| 12 | **Payment gateway** (Razorpay/Stripe) is **mocked** — real API credentials are not used | Avoids live transaction risk; exercise is about architecture |
| 13 | **Email/SMS/Push notification delivery** is simulated via log output — real provider integration is plug-in ready | Twilio/SendGrid/FCM adapters are coded but not activated |
| 14 | **HelpBot** is rule-based (keyword + FAQ matching) — no external LLM or NLP integration | Keeps scope manageable; LLM is an extension point |
| 15 | **MongoDB 7** is used as the primary database for all microservices — relational schema not required | Flexible schema suits diverse entity variations |
| 16 | **H2 in-memory DB** can be substituted for MongoDB in unit/fast tests using Flapdoodle embedded Mongo | Testcontainers used for real integration tests |
| 17 | **Kafka KRaft mode** (no ZooKeeper) is assumed — simplifies local setup | Kafka 3.3+ supports KRaft as production-ready |
| 18 | **Keycloak** runs as a separate container in local dev; in production it is a dedicated EC2 instance | Not deployed inside EKS to avoid complexity |
| 19 | **All timestamps are UTC** — timezone conversion is the caller's responsibility | Avoids ambiguous timezone handling |
| 20 | **All monetary values are in INR** by default; currency field supports multi-currency extension | B2C context is India-focused for this exercise |

---

## 3. Infrastructure / Cloud Assumptions

| # | Assumption | Rationale |
|---|---|---|
| 21 | **AWS** is chosen as the cloud platform (EKS, ECR, MSK, DocumentDB, ElastiCache) | Most common enterprise cloud in Indian market |
| 22 | **Amazon DocumentDB** is used for production MongoDB (MongoDB 7-compatible) | Managed, auto-backup, IAM integration |
| 23 | **Amazon MSK** is used for production Kafka (Kafka 3.x compatible) | Managed, no ZooKeeper/KRaft ops overhead |
| 24 | **Minimum 2 EKS worker nodes** across 2 Availability Zones — ensures HA | Exercise requirement |
| 25 | **Terraform** manages all infrastructure — no manual console changes | IaC for reproducibility |
| 26 | **OIDC** is used for GitHub Actions → AWS auth — no long-lived access keys stored | Security best practice |
| 27 | **Istio** is pre-installed on the EKS cluster before application deployment | Service mesh is a prerequisite |
| 28 | **ArgoCD** is deployed in a separate `argocd` namespace and is cluster-scoped | GitOps controller manages all `asms-prod` resources |
| 29 | **ECR vulnerability scanning** is enabled — builds are not blocked by scan results (warning only in exercise) | In production, critical CVEs would block deploy |
| 30 | **SSL/TLS** terminates at the ALB using AWS ACM certificate — internal traffic uses Istio mTLS | Standard AWS pattern |

---

## 4. Scope Assumptions

| # | Assumption | Rationale |
|---|---|---|
| 31 | **Apartment Forums** feature is identified in the bounded context map but NOT implemented in this exercise | Out of scope per time constraints; mentioned in architecture |
| 32 | **GraphQL** layer is noted in the exercise but not implemented (marked as "Sushil J has to provide") | Explicitly excluded from my scope |
| 33 | **Multi-threading module** (Lift, Electricity, Housing Society) is a **standalone Java module** in the same repo, not a microservice | Educational exercise; not deployed to K8s |
| 34 | **Karate exercises 1.1–1.4** test the public `https://reqres.in` API as specified — not the ASMS APIs | Direct exercise requirement |
| 35 | **SonarQube** is self-hosted on a local/EC2 instance — cloud SonarCloud is an alternative | Exercise mentions sonarqube eclipse plugin |
| 36 | **Caching** is implemented for read-heavy entities (users, amenities) using Redis `@Cacheable` annotations | Performance optimization as required |
| 37 | **Pagination** is implemented on all list endpoints using Spring Data `Pageable` | Exercise requirement |
| 38 | **Circuit breaker** is configured only for cross-service calls (e.g., amenity → payment) — not for DB calls | Resilience4j overhead not justified for DB with Testcontainers |
| 39 | **Kafka event sourcing** is used for audit trail; CRUD operations also persist directly to MongoDB (dual-write for ASMS) | CQRS-lite: eventual consistency acceptable for notifications |
| 40 | **GDPR compliance** — User data deletion propagates via Kafka event to all services; eventual deletion within 72 hours | Compliance SLO from NFR section |

---

## 5. Multi-threading Specific Assumptions

| # | Assumption | Rationale |
|---|---|---|
| 41 | **Lift SCAN algorithm** starts from floor 0 and always sweeps UP first, then DOWN | Standard LOOK/SCAN elevator heuristic |
| 42 | **Floor transit time** is simulated as 500ms per floor | Simulation — not real-world timing |
| 43 | **Power failover check** interval is 500ms polling — acceptable for simulation | Real systems use hardware interrupt, not polling |
| 44 | **Swimming pool capacity** enforcement uses Java Semaphore (fair mode) — no reservation system | Simple occupancy control |
| 45 | **Club room** is booked for single event at a time; concurrent booking attempts immediately denied (tryLock, not wait) | Avoid deadlock in simulation |
| 46 | **Security cameras** run on **Java 21 virtual threads** — one virtual thread per camera | Demonstrates Java 21 Loom advantage over OS thread pool |

---

## 6. Testing Assumptions

| # | Assumption | Rationale |
|---|---|---|
| 47 | **70% code coverage** target applies to the service layer and domain layer; auto-generated code (mappers, builders) excluded | SonarQube excludes generated sources |
| 48 | **Testcontainers** uses Docker Desktop — assumes Docker is running on the CI/CD machine | GitHub Actions ubuntu-latest includes Docker |
| 49 | **Karate tests** for Exercises 1.1–1.4 run against live `reqres.in` — internet access required during test run | Public test API as specified in exercise |
| 50 | **Mockito strict stubs** mode is enabled — prevents unused stub accumulation in large test suites | Code quality |
