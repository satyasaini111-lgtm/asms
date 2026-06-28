# Apartment Service Management System (ASMS)
## ECA Programme – Integrated Exercise

---

## Project Overview

ASMS is a **B2B + B2C microservices platform** for gated community / apartment complex management, built for startup **XYZ**.

| | |
|---|---|
| **Domain** | PropTech / Smart Housing |
| **Architecture** | Cloud-native Microservices |
| **Platform** | AWS (EKS, ECR, MSK, DocumentDB, ElastiCache) |
| **Tech Stack** | Java 21 · Spring Boot 3.3 · MongoDB 7 · Kafka 3.7 (KRaft) · Redis 7 · Kubernetes 1.30 |
| **Author** | Satya Saini |

---

## Document Index

| # | Document | Description |
|---|---|---|
| 01 | [Architecture](01-ARCHITECTURE.md) | BCD, SCD, Logical & Physical diagrams |
| 02 | [Domain Model](02-DOMAIN-MODEL.md) | Entities, MongoDB collections, indexes |
| 03 | [Microservices](03-MICROSERVICES.md) | Bounded context, service catalog, communication |
| 04 | [API Design](04-API-DESIGN.md) | REST API catalog with OpenAPI snippets |
| 05 | [Design Patterns](05-DESIGN-PATTERNS.md) | GOF patterns with Java 21 code examples |
| 06 | [SOLID Principles](06-SOLID-PRINCIPLES.md) | SOLID with concrete code examples |
| 07 | [Multi-threading](07-MULTITHREADING.md) | Lift algorithm, electricity failover, housing society |
| 08 | [Messaging](08-MESSAGING.md) | Kafka, Avro schemas, ksqlDB streams |
| 09 | [DevOps & CI/CD](09-DEVOPS-CICD.md) | GitHub Actions, ArgoCD, Helm, Terraform |
| 10 | [Observability](10-OBSERVABILITY.md) | LGTM stack, OpenTelemetry, dashboards, alerts |
| 11 | [Security](11-SECURITY.md) | Keycloak OAuth2, Spring Security 6, Istio mTLS |
| 12 | [NFR / SLA / SLO](12-NFR-SLA-SLO.md) | Non-functional requirements, SLAs, SLOs, SLIs |
| 13 | [Testing Strategy](13-TESTING.md) | JUnit 5, Mockito, Testcontainers, Karate |
| 14 | [Assumptions](14-ASSUMPTIONS.md) | All assumptions clearly stated |

---

## Quick Start (Local Development)

### Prerequisites
- Docker Desktop 4.x
- Java 21 (Temurin)
- Maven 3.9.x

### Start All Infrastructure
```bash
# Kafka (KRaft), MongoDB 7, Redis 7, Keycloak 24, Schema Registry
docker-compose up -d

# Start monitoring stack (Grafana, Loki, Tempo, Prometheus)
docker-compose -f docker-compose.monitoring.yml up -d
```

### Run a Service
```bash
cd user-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Access Points (local)
| Service | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Keycloak Admin | http://localhost:8180/admin (admin/admin) |
| Kafka UI | http://localhost:8090 |
| Schema Registry | http://localhost:8081 |
| Grafana | http://localhost:3000 (admin/admin) |
| ArgoCD | http://localhost:8443 |
| SonarQube | http://localhost:9000 |

---

## System Use Cases

| # | Use Case System | Module |
|---|---|---|
| 1 | Actors' Lifecycle Management | `user-service` |
| 2 | Amenities / Services Management | `amenity-service` |
| 3 | Generic Workflow Management | `workflow-service` |
| 4 | Customer Support Management | `support-service` |
| 5 | Payment Management | `payment-service` |
| 6 | Billing & Reporting | `billing-service` |
| 7 | Visitor Management | `visitor-service` |
| 8 | OneClick Help Bot | `helpbot-service` |

---

## Actors

| Actor | Role | Access |
|---|---|---|
| Application Admin | Platform super-admin | Full access |
| RWA Committee Member | Society governance | Society-level config |
| Owner | Flat owner | Own unit + amenities |
| Tenant | Flat tenant | Own unit + amenities |
| Vendor / Partner | B2B service provider | Own service catalog |
| Security | Gate management | Visitor management |
| Employee | Society staff | Assigned tasks |
| Domestic Help (Maid/Cook) | Temporary worker | Time-bound access |
| Visitor | Guest | Temp entry pass |

---

## Key Technology Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Java version | Java 21 | Virtual threads eliminate thread pool sizing; records reduce boilerplate |
| No Eureka | K8s DNS | In-cluster service resolution is native; no registry to maintain |
| No ZooKeeper | Kafka KRaft | Simpler ops, faster failover, single cluster to manage |
| No ELK | Grafana LGTM | Unified UI for logs + traces + metrics; Loki cheaper than Elasticsearch |
| No Zuul | Spring Cloud Gateway | Zuul is deprecated; Gateway is reactive and actively maintained |
| GitOps | ArgoCD | Declarative deployments; no kubectl in pipelines; drift detection |
| Auth | Keycloak | Centralized IdP; single JWT validation at gateway; RBAC out of the box |
