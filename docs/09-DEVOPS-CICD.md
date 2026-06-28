# DevOps & CI/CD – ASMS
## GitHub Actions + ArgoCD + Helm + Terraform

---

## 1. CI/CD Pipeline Overview

```
Developer pushes code
        │
        ▼
GitHub Actions (CI)
  ├── Compile & Unit Test (JUnit 5)
  ├── Integration Test (Testcontainers)
  ├── SonarQube Analysis (70%+ coverage gate)
  ├── Docker Multi-stage Build (BuildKit)
  ├── ECR Push (OIDC — no stored AWS keys)
  └── Update Helm values in GitOps repo
              │
              ▼
ArgoCD (CD — GitOps)
  ├── Detects values.yaml change
  ├── Syncs Helm chart to EKS
  ├── Rolling update (zero-downtime)
  └── Self-heal on drift
```

---

## 2. GitHub Actions Pipeline

```yaml
# .github/workflows/ci-cd.yml
name: ASMS CI/CD

on:
  push:
    branches: [main]
    paths:
      - 'user-service/**'
  pull_request:
    branches: [main]
    paths:
      - 'user-service/**'

permissions:
  id-token: write
  contents: read
  checks: write

env:
  AWS_REGION: ap-south-1
  ECR_REGISTRY: ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.ap-south-1.amazonaws.com
  SERVICE: user-service
  JAVA_VERSION: '21'

jobs:
  test:
    name: Build & Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: maven

      - name: Run Unit & Integration Tests
        run: mvn clean verify -pl ${{ env.SERVICE }}

      - name: Publish Test Results
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: JUnit Tests
          path: '${{ env.SERVICE }}/target/surefire-reports/*.xml'
          reporter: java-junit

      - name: SonarQube Scan
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        run: |
          mvn sonar:sonar -pl ${{ env.SERVICE }} \
            -Dsonar.projectKey=asms-${{ env.SERVICE }} \
            -Dsonar.qualitygate.wait=true

  build-push:
    name: Build & Push Docker Image
    needs: test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    outputs:
      image-tag: ${{ steps.meta.outputs.version }}
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials (OIDC — no long-lived keys)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/github-actions-ecr-role
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to ECR
        uses: aws-actions/amazon-ecr-login@v2

      - name: Set up Docker Buildx (BuildKit)
        uses: docker/setup-buildx-action@v3

      - name: Extract image metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.ECR_REGISTRY }}/${{ env.SERVICE }}
          tags: |
            type=sha,prefix=
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Build & Push (layered, cached)
        uses: docker/build-push-action@v5
        with:
          context: ./${{ env.SERVICE }}
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: ECR vulnerability scan
        run: |
          aws ecr describe-image-scan-findings \
            --repository-name ${{ env.SERVICE }} \
            --image-id imageTag=${{ steps.meta.outputs.version }} \
            --query 'imageScanFindings.findingSeverityCounts' || true

  gitops-update:
    name: Update GitOps Repo
    needs: build-push
    runs-on: ubuntu-latest
    steps:
      - name: Checkout GitOps repo
        uses: actions/checkout@v4
        with:
          repository: org/asms-gitops
          token: ${{ secrets.GITOPS_PAT }}
          path: gitops

      - name: Update image tag in Helm values
        run: |
          cd gitops
          yq e ".image.tag = \"${{ needs.build-push.outputs.image-tag }}\"" \
            -i helm/${{ env.SERVICE }}/values-prod.yaml
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git commit -am "ci: ${{ env.SERVICE }} → ${{ needs.build-push.outputs.image-tag }}"
          git push
```

---

## 3. Dockerfile (Multi-stage, Java 21, Layered)

```dockerfile
# ---- Stage 1: Dependency Cache ----
FROM eclipse-temurin:21-jdk-alpine AS deps
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# ---- Stage 2: Build ----
FROM deps AS builder
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ---- Stage 3: Layer Extract ----
FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /build
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ---- Stage 4: Runtime (minimal) ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S asms && adduser -S asmsuser -G asms
WORKDIR /app

# Copy layered contents (most stable layers first = faster rebuilds)
COPY --from=extractor /build/dependencies/          ./
COPY --from=extractor /build/spring-boot-loader/    ./
COPY --from=extractor /build/snapshot-dependencies/ ./
COPY --from=extractor /build/application/           ./

USER asmsuser
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
  "org.springframework.boot.loader.launch.JarLauncher"]
```

---

## 4. Helm Chart Structure

```
helm/user-service/
├── Chart.yaml
├── values.yaml           ← defaults
├── values-dev.yaml       ← dev overrides
├── values-prod.yaml      ← prod (image tag updated by CI)
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── hpa.yaml
    ├── configmap.yaml
    ├── secret.yaml
    ├── serviceaccount.yaml
    ├── ingress.yaml
    └── pdb.yaml          ← PodDisruptionBudget
```

### Chart.yaml
```yaml
apiVersion: v2
name: user-service
description: ASMS User Service
type: application
version: 1.0.0
appVersion: "1.0.0"
```

### values.yaml
```yaml
replicaCount: 2
image:
  repository: <account>.dkr.ecr.ap-south-1.amazonaws.com/user-service
  tag: latest
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 8081

resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
  limits:
    cpu: "500m"
    memory: "1Gi"

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70

env:
  SPRING_PROFILES_ACTIVE: prod
  SPRING_DATA_MONGODB_URI: ""   # injected from secret
  SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka-1:9092,kafka-2:9092,kafka-3:9092
  KEYCLOAK_ISSUER_URI: https://keycloak.asms.io/realms/asms

probes:
  liveness:
    path: /actuator/health/liveness
    initialDelaySeconds: 60
    periodSeconds: 30
  readiness:
    path: /actuator/health/readiness
    initialDelaySeconds: 30
    periodSeconds: 10
```

### templates/deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "user-service.fullname" . }}
  labels:
    {{- include "user-service.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "user-service.selectorLabels" . | nindent 6 }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        {{- include "user-service.selectorLabels" . | nindent 8 }}
      annotations:
        instrumentation.opentelemetry.io/inject-java: "true"  # OTEL auto-inject
    spec:
      serviceAccountName: {{ include "user-service.serviceAccountName" . }}
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: 8081
          envFrom:
            - configMapRef:
                name: {{ include "user-service.fullname" . }}-config
            - secretRef:
                name: {{ include "user-service.fullname" . }}-secret
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness.path }}
              port: 8081
            initialDelaySeconds: {{ .Values.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness.path }}
              port: 8081
            initialDelaySeconds: {{ .Values.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

### templates/hpa.yaml
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "user-service.fullname" . }}-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "user-service.fullname" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
```

---

## 5. ArgoCD Application

```yaml
# k8s/argocd/user-service-app.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: asms-user-service
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: asms
  source:
    repoURL: https://github.com/org/asms-gitops
    targetRevision: HEAD
    path: helm/user-service
    helm:
      valueFiles:
        - values.yaml
        - values-prod.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: asms-prod
  syncPolicy:
    automated:
      prune: true        # remove deleted resources
      selfHeal: true     # revert manual kubectl changes
    syncOptions:
      - CreateNamespace=true
      - PrunePropagationPolicy=foreground
    retry:
      limit: 3
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
```

---

## 6. Terraform – AWS Infrastructure

```hcl
# terraform/main.tf

provider "aws" {
  region = var.aws_region
}

# EKS Cluster
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = "asms-eks"
  cluster_version = "1.30"
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnets

  eks_managed_node_groups = {
    general = {
      instance_types = ["t3.large"]
      min_size       = 2
      max_size       = 5
      desired_size   = 2
    }
  }

  cluster_addons = {
    coredns    = { most_recent = true }
    kube-proxy = { most_recent = true }
    vpc-cni    = { most_recent = true }
    aws-ebs-csi-driver = { most_recent = true }
  }
}

# Amazon MSK (Kafka)
resource "aws_msk_cluster" "asms_kafka" {
  cluster_name           = "asms-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type  = "kafka.m5.large"
    client_subnets = module.vpc.private_subnets
    storage_info {
      ebs_storage_info { volume_size = 100 }
    }
    security_groups = [aws_security_group.msk.id]
  }

  encryption_info {
    encryption_in_transit { client_broker = "TLS" }
  }
}

# Amazon DocumentDB (MongoDB-compatible)
resource "aws_docdb_cluster" "asms_docdb" {
  cluster_identifier      = "asms-docdb"
  engine                  = "docdb"
  master_username         = var.db_username
  master_password         = var.db_password
  backup_retention_period = 7
  preferred_backup_window = "02:00-04:00"
  skip_final_snapshot     = false
  vpc_security_group_ids  = [aws_security_group.docdb.id]
  db_subnet_group_name    = aws_docdb_subnet_group.asms.name
}

resource "aws_docdb_cluster_instance" "asms_docdb_instances" {
  count              = 3
  identifier         = "asms-docdb-${count.index}"
  cluster_identifier = aws_docdb_cluster.asms_docdb.id
  instance_class     = "db.r6g.large"
}

# ElastiCache Redis
resource "aws_elasticache_replication_group" "asms_redis" {
  replication_group_id = "asms-redis"
  description          = "ASMS Redis cluster"
  node_type            = "cache.r6g.large"
  num_cache_clusters   = 2
  engine_version       = "7.2"
  port                 = 6379
  security_group_ids   = [aws_security_group.redis.id]
  subnet_group_name    = aws_elasticache_subnet_group.asms.name

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}

# GitHub OIDC Provider (no stored AWS access keys in GitHub)
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

resource "aws_iam_role" "github_actions" {
  name = "github-actions-ecr-role"
  assume_role_policy = jsonencode({
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:org/asms:*"
        }
      }
    }]
  })
}
```

---

## 7. Security Groups

```hcl
# sg-alb: internet-facing
resource "aws_security_group" "alb" {
  name   = "sg-asms-alb"
  vpc_id = module.vpc.vpc_id

  ingress { from_port = 80  to_port = 80  protocol = "tcp" cidr_blocks = ["0.0.0.0/0"] }
  ingress { from_port = 443 to_port = 443 protocol = "tcp" cidr_blocks = ["0.0.0.0/0"] }
  egress  { from_port = 0   to_port = 0   protocol = "-1"  cidr_blocks = ["0.0.0.0/0"] }
}

# sg-eks: EKS nodes — only from ALB
resource "aws_security_group" "eks_nodes" {
  name   = "sg-asms-eks"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port       = 8080
    to_port         = 8089
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
}

# sg-docdb: DocumentDB — only from EKS nodes
resource "aws_security_group" "docdb" {
  name   = "sg-asms-docdb"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }
}
```

---

## 8. Istio Service Mesh

```yaml
# k8s/istio/peer-authentication.yaml — enforce mTLS everywhere
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: asms-prod
spec:
  mtls:
    mode: STRICT   # reject all non-mTLS traffic between pods

---
# k8s/istio/virtual-service-payment.yaml — retry policy
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: payment-service
  namespace: asms-prod
spec:
  hosts: [payment-service]
  http:
    - retries:
        attempts: 3
        perTryTimeout: 2s
        retryOn: "gateway-error,connect-failure,retriable-4xx"
      timeout: 8s
      route:
        - destination:
            host: payment-service
            port:
              number: 8085
```

---

## 9. Local docker-compose (Full Dev Stack)

```yaml
# docker-compose.yml
version: '3.9'
services:
  mongodb:
    image: mongo:7.0
    ports: ["27017:27017"]
    volumes: ["mongo-data:/data/db"]

  redis:
    image: redis/redis-stack:7.2.0-v9
    ports: ["6379:6379", "8001:8001"]  # 8001 = RedisInsight UI

  kafka:
    image: confluentinc/cp-kafka:7.6.1
    ports: ["9092:9092"]
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'

  schema-registry:
    image: confluentinc/cp-schema-registry:7.6.1
    ports: ["8081:8081"]
    environment:
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:9092
      SCHEMA_REGISTRY_HOST_NAME: schema-registry

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    ports: ["8180:8080"]
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev

  ksqldb:
    image: confluentinc/cp-ksqldb-server:7.6.1
    ports: ["8088:8088"]
    environment:
      KSQL_BOOTSTRAP_SERVERS: kafka:9092
      KSQL_KSQL_SCHEMA_REGISTRY_URL: http://schema-registry:8081

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports: ["8090:8080"]
    environment:
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
      KAFKA_CLUSTERS_0_SCHEMAREGISTRY: http://schema-registry:8081

volumes:
  mongo-data:
```
