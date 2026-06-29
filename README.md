# Apartment Service Management System (ASMS)

A cloud-native microservices application for managing apartment services — built as part of the Publicis Sapient ECA programme. The system handles resident management, amenity bookings, visitor access, payments, billing, support tickets, and automated workflows across 11 independently deployable services.

---

## Architecture Overview

```
Internet
   │
   ▼
AWS ALB (internet-facing)
   │
   ▼
API Gateway  (:8080)          ← Spring Cloud Gateway — single entry point
   │
   ├── /api/v1/users      →  User Service        (:8081)
   ├── /api/v1/amenities  →  Amenity Service     (:8083)
   ├── /api/v1/tickets    →  Support Service     (:8084)
   ├── /api/v1/visitors   →  Visitor Service     (:8085)
   ├── /api/v1/payments   →  Payment Service     (:8086)
   ├── /api/v1/invoices   →  Billing Service     (:8087)
   ├── /api/v1/workflows  →  Workflow Service    (:8088)
   ├── /api/v1/helpbot    →  Helpbot Service     (:8090)
   └── /actuator          →  (gateway health)

Shared Infrastructure (same namespace):
  Config Server  (:8888)   ← Spring Cloud Config — centralised config
  MongoDB        (:27017)  ← Primary document store (StatefulSet)
  Redis          (:6379)   ← Session cache / pub-sub
  Kafka          (:9092)   ← Async event streaming (KRaft mode)
  Keycloak       (:8080)   ← Identity & Access Management
```

---

## Microservices

| Service | Port | Description |
|---|---|---|
| `api-gateway` | 8080 | Spring Cloud Gateway — routes all inbound traffic, JWT validation |
| `config-server` | 8888 | Spring Cloud Config Server — centralised configuration |
| `user-service` | 8081 | Resident registration, profiles, authentication via Keycloak |
| `amenity-service` | 8083 | Amenity catalogue, slot availability, booking management |
| `support-service` | 8084 | Help-desk ticket creation, assignment, and resolution |
| `visitor-service` | 8085 | Visitor pre-registration, gate-pass generation, access logs |
| `payment-service` | 8086 | Payment processing, transaction history |
| `billing-service` | 8087 | Invoice generation, recurring charges, billing cycles |
| `workflow-service` | 8088 | Approval workflows, task orchestration across services |
| `notification-service` | 8089 | Email / push notifications via Kafka events |
| `helpbot-service` | 8090 | AI-assisted self-service support chatbot |

---

## Technology Stack

### Application
| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.11 |
| Service Mesh Config | Spring Cloud 2023.0.3 |
| API Gateway | Spring Cloud Gateway |
| Security | Spring Security + Keycloak 24.0.5 (OIDC/JWT) |
| Database | MongoDB 7.0 (Spring Data MongoDB 4.3.4) |
| Messaging | Apache Kafka 7.6.1 (KRaft mode, Confluent CP) + Avro 1.11.3 |
| Cache | Redis 7.2 |
| Resilience | Resilience4j 2.2.0 (circuit breaker, retry, rate limiter) |
| API Docs | SpringDoc OpenAPI 2.5.0 |
| Build | Maven 3.9 (multi-module) |

### Observability
| Tool | Purpose |
|---|---|
| OpenTelemetry 1.37.0 | Distributed tracing (auto-instrumentation via Java agent) |
| Micrometer 1.13.4 | Metrics collection |
| Prometheus | Metrics scraping (`/actuator/prometheus`) |
| Logstash Logback 7.4 | Structured JSON logging |

### Infrastructure
| Layer | Technology |
|---|---|
| Cloud | AWS (us-east-1) |
| Container Registry | Amazon ECR |
| Orchestration | Amazon EKS 1.36 (Auto Mode) |
| Compute | AWS Fargate (serverless pods) |
| Load Balancer | AWS ALB (via AWS Load Balancer Controller) |
| IaC | Terraform >= 1.6 (AWS provider ~5.50) |
| State Backend | S3 + DynamoDB locking |
| CI/CD | Jenkins (Declarative Pipeline) |
| Code Quality | SonarQube |
| GitOps (optional) | Argo CD |

---

## Infrastructure

Provisioned with Terraform under `infra/terraform/`:

```
infra/terraform/
├── main.tf          # Provider config, S3 backend
├── vpc.tf           # VPC, public/private subnets, IGW, route tables
├── security-groups.tf
├── nacl.tf
├── eks.tf           # EKS 1.36 cluster (Auto Mode), OIDC provider, IAM roles
├── fargate.tf       # Fargate profile for asms-prod namespace
├── ecr.tf           # ECR repositories for all 11 services
├── variables.tf
└── outputs.tf
```

### Key AWS Resources
- **VPC**: Custom VPC with public + private subnets across multiple AZs
- **EKS Cluster**: `asms-prod-eks` — EKS 1.36 with Auto Mode enabled (manages compute, networking, and load balancing automatically)
- **Fargate Profile**: Pods in `asms-prod` namespace run serverless on Fargate
- **ECR**: One repository per service under the `asms/` prefix; third-party images (MongoDB, Redis, Kafka, Keycloak, Busybox) mirrored to ECR to avoid Docker Hub rate limits

### Terraform Bootstrap

```bash
# One-time: create S3 bucket and DynamoDB table for state
aws s3 mb s3://asms-terraform-state-prod --region us-east-1
aws dynamodb create-table \
  --table-name asms-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1

cd infra/terraform
terraform init
terraform plan
terraform apply
```

---

## CI/CD Pipeline

Jenkins Declarative Pipeline (`Jenkinsfile`) — 60-minute timeout, no concurrent builds.

### Pipeline Stages

```
Checkout
   │
Unit Tests          (user, support, payment, visitor services)
   │
Build JARs          (mvn package -DskipTests, all modules)
   │
SonarQube Analysis  (conditional: $SONAR_HOST_URL set)
   │
ECR Login           (conditional: $AWS_ACCOUNT_ID set)
   │
Mirror Infra Images (idempotent: mongo, redis, kafka, keycloak, busybox → ECR)
   │
Docker Build & Push (parallel: all 11 services built and pushed to ECR)
   │
Deploy to EKS
   ├── Apply infra (MongoDB StatefulSet, Redis, Kafka, Keycloak)
   ├── Force-delete StatefulSet pods (EKS Auto Mode workaround — see notes)
   ├── Wait: MongoDB ready (600s), Kafka ready (300s)
   ├── Apply all service deployments (sed-substitutes ECR_REGISTRY + IMAGE_TAG)
   ├── Apply Ingress
   ├── Wait: api-gateway rollout (600s)
   └── Wait: user-service rollout (600s)
   │
Smoke Test
   ├── Wait for ALB hostname (up to 5 min, 20 × 15s polls)
   ├── Sleep 60s (ALB health check warm-up)
   └── curl /api/v1/users/actuator/health (retry 6×, 15s delay)
```

### Jenkins Setup Requirements

| Requirement | Detail |
|---|---|
| Jenkins credential | `aws-credentials` (Username=AWS_ACCESS_KEY_ID, Password=AWS_SECRET_ACCESS_KEY) |
| Jenkins env var | `AWS_ACCOUNT_ID` — your 12-digit AWS account ID |
| JDK tool | Named `JDK-21` (Java 21) |
| Maven tool | Named `Maven-3.9` |
| Docker | Available on Jenkins agent |
| kubectl | Available on Jenkins agent, configured for `asms-prod-eks` |
| aws CLI | v2, available on Jenkins agent |

### Image Tagging

Images are tagged as `<BUILD_NUMBER>-<GIT_COMMIT_SHORT>` (e.g., `29-a4b3c2d`).

---

## Kubernetes Layout

All resources live in the `asms-prod` namespace.

```
k8s/
├── base/
│   ├── namespace.yaml
│   ├── configmap.yaml            # asms-common-config (shared env vars)
│   ├── secrets.yaml              # MongoDB creds, per-service DB URIs, Keycloak creds
│   ├── infra-deployments.yaml    # MongoDB, Redis, Kafka, Keycloak StatefulSets/Deployments
│   ├── ingress.yaml              # ALB Ingress (internet-facing, IP target mode)
│   ├── api-gateway-deployment.yaml
│   ├── config-server-deployment.yaml
│   ├── user-service-deployment.yaml
│   ├── amenity-service-deployment.yaml
│   ├── support-service-deployment.yaml
│   ├── visitor-service-deployment.yaml
│   ├── payment-service-deployment.yaml
│   ├── billing-service-deployment.yaml
│   ├── workflow-service-deployment.yaml
│   ├── notification-service-deployment.yaml
│   ├── helpbot-service-deployment.yaml
│   └── istio-policies.yaml
├── otel/
│   └── instrumentation.yaml      # OpenTelemetry Java auto-instrumentation
├── monitoring/
│   └── prometheus-rules.yaml
└── argocd/
    └── asms-app.yaml             # Argo CD Application manifest
```

### Shared Configuration (ConfigMap)

```yaml
SPRING_DATA_MONGODB_URI:  mongodb://asms:***@mongodb:27017/asms?authSource=admin
KAFKA_BOOTSTRAP_SERVERS:  kafka:9092
REDIS_HOST:               redis
REDIS_PORT:               6379
SPRING_PROFILES_ACTIVE:   prod
KEYCLOAK_ISSUER_URI:      http://keycloak:8080/realms/asms
KEYCLOAK_JWK_URI:         http://keycloak:8080/realms/asms/protocol/openid-connect/certs
```

### Probe Configuration (all domain services)

Java 21 Spring Boot services on Fargate cold-start in 80–100 seconds:

```yaml
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: <service-port> }
  initialDelaySeconds: 90    # wait for JVM + Spring context init
  periodSeconds: 10
  failureThreshold: 6
  timeoutSeconds: 5

livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: <service-port> }
  initialDelaySeconds: 120   # hard minimum — pod would be killed before startup otherwise
  periodSeconds: 15
  failureThreshold: 3
  timeoutSeconds: 5
```

### Resource Limits (per pod)

| Resource | Request | Limit |
|---|---|---|
| Memory | 256 Mi | 512 Mi |
| CPU | 100 m (150 m for api-gateway) | 500 m (600 m for api-gateway) |

### HPA

All domain services have a HorizontalPodAutoscaler (CPU 70%, Memory 80%):

| Service | Min | Max |
|---|---|---|
| api-gateway | 1 | 8 |
| user-service | 1 | 6 |
| All others | 1 | 4–6 |

---

## Known EKS Auto Mode Workarounds

### StatefulSet Rolling Update Deadlock
EKS Auto Mode silently ignores `maxUnavailable` in StatefulSet `rollingUpdate`. The controller waits for the current pod to become `Unavailable` before creating the replacement, but a `Running` pod is never considered unavailable — causing an infinite wait. **Fix**: force-delete `mongodb-0` and `kafka-0` after each `kubectl apply`. The controller detects a *missing* pod and immediately recreates it from `updateRevision`.

```bash
kubectl delete pod mongodb-0 kafka-0 -n asms-prod --ignore-not-found=true
```

### Kafka KRaft Single-Node Binding
Kafka must bind listeners to `0.0.0.0`, not to the pod hostname, for KRaft mode to initialise correctly:

```
KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093,PLAINTEXT_HOST://0.0.0.0:9092
KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:29093
```

### CoreDNS
EKS Auto Mode does not pre-install CoreDNS. Install it as a managed addon after cluster creation:

```bash
aws eks create-addon --cluster-name asms-prod-eks --addon-name coredns --region us-east-1
```

### Java 21 Minimum Thread Stack Size
Java 21 raised the minimum `-Xss` to 136 KB. All Dockerfiles use `-Xss512k`.

---

## Local Development

### Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop
- kubectl
- AWS CLI v2
- Terraform >= 1.6

### Build All Modules

```bash
mvn package -DskipTests
```

### Run Unit Tests

```bash
mvn test -pl user-service,support-service,payment-service,visitor-service
```

### Run a Single Service Locally

Each service reads config from `application-local.yml`. Start infra dependencies with Docker first:

```bash
docker run -d --name mongodb -p 27017:27017 -e MONGO_INITDB_ROOT_USERNAME=asms -e MONGO_INITDB_ROOT_PASSWORD=asms-secret mongo:7.0
docker run -d --name redis -p 6379:6379 redis:7.2-alpine

# Then run any service
mvn spring-boot:run -pl user-service -Dspring-boot.run.profiles=local
```

---

## API Endpoints

All requests go through the ALB → API Gateway. Obtain the ALB hostname:

```bash
kubectl get ingress asms-ingress -n asms-prod -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

| Path Prefix | Service | Example |
|---|---|---|
| `/api/v1/users` | user-service | `GET /api/v1/users/actuator/health` |
| `/api/v1/amenities` | amenity-service | `GET /api/v1/amenities` |
| `/api/v1/tickets` | support-service | `POST /api/v1/tickets` |
| `/api/v1/visitors` | visitor-service | `POST /api/v1/visitors` |
| `/api/v1/payments` | payment-service | `POST /api/v1/payments` |
| `/api/v1/invoices` | billing-service | `GET /api/v1/invoices` |
| `/api/v1/workflows` | workflow-service | `GET /api/v1/workflows` |
| `/api/v1/helpbot` | helpbot-service | `POST /api/v1/helpbot/chat` |
| `/actuator/health` | api-gateway | health check |

OpenAPI docs available at `/swagger-ui.html` on each service's port when running locally.

---

## Observability

- **Metrics**: Prometheus scrapes `/actuator/prometheus` on all pods (annotated with `prometheus.io/scrape: "true"`)
- **Traces**: OpenTelemetry Java agent auto-injects distributed traces; service name set from `app` pod label
- **Logs**: JSON structured logs via Logstash Logback encoder
- **Alerting**: Prometheus alerting rules in `k8s/monitoring/prometheus-rules.yaml`

---

## Project Structure

```
asms/
├── Jenkinsfile                  # CI/CD pipeline
├── pom.xml                      # Maven multi-module parent
├── api-gateway/
├── config-server/
├── user-service/
├── amenity-service/
├── support-service/
├── visitor-service/
├── payment-service/
├── billing-service/
├── workflow-service/
├── notification-service/
├── helpbot-service/
├── multithreading-module/       # Shared concurrency utilities
├── infra/
│   └── terraform/               # AWS infrastructure as code
└── k8s/
    ├── base/                    # Kubernetes manifests
    ├── otel/                    # OpenTelemetry instrumentation
    ├── monitoring/              # Prometheus rules
    └── argocd/                  # GitOps app definition
```
