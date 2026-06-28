# Observability – Grafana LGTM Stack + OpenTelemetry
## Logs · Metrics · Traces · Alerts

---

## 1. Stack Overview

```
Every microservice
        │
        │ (OTEL Java agent auto-attached via K8s operator)
        ▼
OpenTelemetry Collector (Grafana Alloy)
        │
   ┌────┴──────────────────────────┐
   │           │                   │
   ▼           ▼                   ▼
Prometheus   Grafana Loki      Grafana Tempo
(metrics)    (logs)            (traces)
   │           │                   │
   └────┬──────┘───────────────────┘
        ▼
   Grafana 11
   (unified dashboard)
```

---

## 2. OpenTelemetry Auto-Instrumentation

### K8s Operator Injection (zero code change)
```yaml
# k8s/otel/instrumentation.yaml
apiVersion: opentelemetry.io/v1alpha1
kind: Instrumentation
metadata:
  name: asms-instrumentation
  namespace: asms-prod
spec:
  exporter:
    endpoint: http://otel-collector:4317
  propagators:
    - tracecontext   # W3C standard
    - baggage
  sampler:
    type: parentbased_traceidratio
    argument: "0.1"  # 10% sampling in prod
  java:
    image: ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:1.37.0
    env:
      - name: OTEL_SERVICE_NAME
        valueFrom:
          fieldRef:
            fieldPath: metadata.labels['app']
      - name: OTEL_LOGS_EXPORTER
        value: otlp
      - name: OTEL_METRICS_EXPORTER
        value: prometheus
```

```yaml
# Add to each Deployment pod template
metadata:
  annotations:
    instrumentation.opentelemetry.io/inject-java: "true"
```

### application.yml (Micrometer configuration)
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics,loggers,env
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 50ms,100ms,200ms,500ms,1s,2s
      percentiles:
        http.server.requests: 0.5,0.75,0.95,0.99
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
  tracing:
    sampling:
      probability: 1.0   # 100% in dev, set 0.1 via env in prod
```

---

## 3. Structured Logging (Logback → Loki)

```xml
<!-- src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <springProfile name="prod,staging">
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <includeMdcKeyName>traceFlags</includeMdcKeyName>
        <customFields>{"service":"${spring.application.name}","env":"${spring.profiles.active}"}</customFields>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON_STDOUT"/>
    </root>
  </springProfile>

  <springProfile name="local,test">
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>
</configuration>
```

**Sample log line (JSON)**:
```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":  "00f067aa0ba902b7",
  "traceFlags": "01",
  "service": "user-service",
  "env": "prod",
  "logger": "com.asms.user.application.UserService",
  "message": "User registered: usr_01J...",
  "thread": "virtual-6"
}
```

---

## 4. Grafana Alloy Config (log + trace pipeline)

```hcl
# k8s/otel/alloy-config.yaml (ConfigMap)

// Collect logs from all pods in asms-prod namespace
discovery.kubernetes "pods" {
  role = "pod"
  namespaces { names = ["asms-prod"] }
}

loki.source.kubernetes "pods" {
  targets    = discovery.kubernetes.pods.targets
  forward_to = [loki.write.default.receiver]
}

loki.write "default" {
  endpoint { url = "http://loki:3100/loki/api/v1/push" }
  external_labels = { cluster = "asms-eks", environment = "prod" }
}

// Receive OTLP traces and forward to Tempo
otelcol.receiver.otlp "default" {
  grpc { endpoint = "0.0.0.0:4317" }
  http { endpoint = "0.0.0.0:4318" }
  output {
    traces = [otelcol.exporter.otlp.tempo.input]
  }
}

otelcol.exporter.otlp "tempo" {
  client { endpoint = "http://tempo:4317" }
}

// Prometheus scrape
prometheus.scrape "spring_boot" {
  targets = discovery.kubernetes.pods.targets
  forward_to = [prometheus.remote_write.default.receiver]
  scrape_interval = "15s"
  metrics_path = "/actuator/prometheus"
}

prometheus.remote_write "default" {
  endpoint { url = "http://prometheus:9090/api/v1/write" }
}
```

---

## 5. Prometheus Alert Rules

```yaml
# k8s/monitoring/alert-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: asms-alerts
  namespace: monitoring
spec:
  groups:
    - name: asms.availability
      rules:
        - alert: HighCpuUsage
          expr: |
            avg(rate(process_cpu_usage[5m])) by (application) > 0.90
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "High CPU on {{ $labels.application }}"
            description: "CPU usage > 90% for 5 minutes on {{ $labels.application }}"

        - alert: HighMemoryUsage
          expr: |
            (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.80
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "High heap memory on {{ $labels.application }}"

        - alert: Http5xxErrors
          expr: |
            sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) by (application) > 100
          labels:
            severity: warning
          annotations:
            summary: "High 5xx error rate on {{ $labels.application }}"

        - alert: HighResponseTime
          expr: |
            histogram_quantile(0.95,
              sum(rate(http_server_requests_seconds_bucket[5m])) by (application, le)
            ) > 2.0
          labels:
            severity: warning
          annotations:
            summary: "p95 latency > 2s on {{ $labels.application }}"

        - alert: KafkaConsumerLag
          expr: |
            kafka_consumer_group_lag > 1000
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "Kafka consumer lag > 1000 on {{ $labels.group }}"
```

---

## 6. Grafana Dashboards

### Dashboard 1: Service Overview
```
Panels:
┌─────────────────────┬─────────────────────┬─────────────────────┐
│  TPS (req/s)        │  Error Rate (5xx%)  │  P95 Latency (ms)   │
│  rate(http_count)   │  5xx/total          │  histogram_quantile  │
├─────────────────────┼─────────────────────┼─────────────────────┤
│  Active Users       │  Kafka Consumer Lag │  Circuit Breaker     │
│  (session count)    │  per topic          │  State              │
├─────────────────────┴─────────────────────┴─────────────────────┤
│  HTTP Response Time Heatmap (by endpoint)                        │
│  Downstream Service Latency (payment-service, booking calls)     │
└──────────────────────────────────────────────────────────────────┘
```

### Dashboard 2: JVM & Infrastructure
```
Panels:
┌─────────────────────┬─────────────────────┬─────────────────────┐
│  Heap Memory Used   │  GC Pause Time      │  Thread Count       │
│  (MB, per service)  │  (ms, G1GC pauses)  │  (virtual threads)  │
├─────────────────────┼─────────────────────┼─────────────────────┤
│  CPU Usage %        │  Memory Usage %     │  Pod Count (HPA)    │
│  (per pod)          │  (node level)       │  min/current/max    │
├─────────────────────┴─────────────────────┴─────────────────────┤
│  JVM Classloading / Non-heap / Metaspace                        │
└──────────────────────────────────────────────────────────────────┘
```

### Dashboard 3: Business Metrics
```
Panels:
┌─────────────────────┬─────────────────────┬─────────────────────┐
│  Tickets Raised     │  Tickets Resolved   │  Avg Resolution Time│
│  (today / week)     │  (today / week)     │  (hours)            │
├─────────────────────┼─────────────────────┼─────────────────────┤
│  Visitor Approvals  │  Payments Today     │  Invoices Overdue   │
│  (approved/rejected)│  (total + amount)   │  (count + value)    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 7. Useful PromQL Queries

```promql
# TPS per service
sum(rate(http_server_requests_seconds_count[1m])) by (application)

# P95 latency per service
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (application, le)
)

# Error rate per service
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
/
sum(rate(http_server_requests_seconds_count[5m])) by (application)
* 100

# Heap usage percentage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# GC pause rate
rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])

# Kafka consumer lag
sum(kafka_consumer_group_lag) by (consumergroup, topic)

# Redis cache hit rate
redis_keyspace_hits_total / (redis_keyspace_hits_total + redis_keyspace_misses_total) * 100
```

---

## 8. Distributed Trace Example (Grafana Tempo)

```
TraceId: 4bf92f3577b34da6a3ce929d0e0e4736

api-gateway          [0ms ─────────────────────── 245ms]
  ├── user-service   [5ms ──── 45ms]   (JWT validation + DB read)
  ├── amenity-service[50ms ─── 120ms]  (availability check)
  │     └── mongodb  [52ms ── 115ms]   (query)
  └── payment-service[125ms ─ 240ms]   (payment initiation)
        └── redis    [126ms ─ 128ms]   (idempotency check)
        └── kafka    [130ms ─ 132ms]   (event publish)
```

In Grafana UI: click any span → see attributes, logs correlated by traceId, service graph

---

## 9. Access Logs (Spring Boot + Actuator)

```yaml
# application.yml — enable access logs
server:
  tomcat:
    accesslog:
      enabled: true
      pattern: '%{yyyy-MM-dd HH:mm:ss}t %s "%r" %b %D ms traceId=%{X-Trace-Id}o'
      directory: /logs
      prefix: access

logging:
  level:
    org.springframework.web.filter.CommonsRequestLoggingFilter: DEBUG
```
