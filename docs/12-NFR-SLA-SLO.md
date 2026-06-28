# Non-Functional Requirements – SLA / SLO / SLI
## Apartment Service Management System (ASMS)

---

## 1. SLA Definitions

### B2B Service Partners (Vendors)

| SLA Area | Target | Penalty on Breach |
|---|---|---|
| Platform Uptime | 99.9% per calendar month | 10% service fee credit per 0.1% below target |
| API Response Time | p95 < 500ms for all CRUD ops | Credit if sustained > 1s for > 30 minutes |
| Vendor Onboarding | Approval within 2 business days | Escalation to account manager |
| Incident Response | P1: 30 min, P2: 4 hours, P3: 24 hours | SLA credit proportional to breach duration |
| Data Retention | 3 years for billing/transaction records | Non-negotiable contractual obligation |
| Support Response | Initial response within 4 business hours | Credit if no response in 8 hours |

### B2C Apartment Residents

| SLA Area | Target | Penalty on Breach |
|---|---|---|
| Platform Uptime | 99.5% per calendar month | Pro-rata maintenance fee waiver |
| Notification Delivery | Push notification within 30 seconds | N/A (best effort) |
| Payment Processing | Success rate > 99.5% | Full refund if payment debited but not confirmed |
| Support Ticket | P1: 4hr, P2: 24hr, P3: 72hr response | Escalation, no fee for that period |
| Data Privacy | User data deletion within 72 hours of request | GDPR compliance — legal obligation |
| Planned Maintenance | 48-hour advance notice | If not notified: 1-day service credit |

---

## 2. SLO Table

| # | SLO Area | SLO Target | How Measured |
|---|---|---|---|
| 1 | **Availability** | 99.9% uptime per month (≤ 43 min downtime) | Synthetic health checks every 60s via Grafana Synthetic Monitoring; `UP / (UP + DOWN)` over 30-day window |
| 2 | **Response Time / Performance** | p95 < 300ms, p99 < 1s for all API endpoints | Prometheus histogram: `histogram_quantile(0.95, ...)` |
| 3 | **Incident Response** | P1 acknowledged in 30 min, resolved in 4hr | PagerDuty alert timestamp → first-response timestamp delta |
| 4 | **Capacity** | Handle 1000 concurrent users, 500 TPS at < 500ms p95 | Gatling load tests (monthly); auto-scale triggers below 80% CPU |
| 5 | **Scalability** | New pod ready within 2 minutes at 80% CPU threshold | HPA `lastScaleTime` metric; Prometheus track pod count change timing |
| 6 | **Data Backup & Recovery** | RPO ≤ 1 hour, RTO ≤ 4 hours | DocumentDB automated backups every 15 min; quarterly restore drill |
| 7 | **Security** | Zero critical CVEs in production images | ECR scan on every push; SonarQube quality gate blocks deploy on critical |
| 8 | **Support** | 90% of P2/P3 tickets resolved within SLA | ksqlDB ticket age query; Grafana business metrics dashboard |
| 9 | **Compatibility** | REST APIs backward-compatible for minimum 2 major versions | API versioning (/v1, /v2); contract tests via Spring Cloud Contract |
| 10 | **Compliance / Regulatory** | GDPR data deletion completed within 72 hours | Audit log query: `request_to_deletion_time` < 72h |
| 11 | **Maintainability** | Full deployment of any service in < 15 minutes | GitHub Actions workflow duration metric |
| 12 | **Usability & Accessibility** | All API error messages human-readable; no raw stack traces | Manual API testing; error response review in QA gate |
| 13 | **Payment Reliability** | Payment success rate > 99.5% | `payment.completed / payment.initiated` ratio in Prometheus |
| 14 | **Notification Freshness** | Kafka consumer lag < 1000 msgs | `kafka_consumer_group_lag` metric in Grafana |

---

## 3. SLI Definitions

### Error Rate
```promql
# Proportion of failed requests
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
/
sum(rate(http_server_requests_seconds_count[5m])) by (application)
```
**SLO**: Error rate < 0.1% (99.9% success rate)

---

### Latency (p95 / p99)
```promql
# p95 response time per service
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (application, uri, le)
)

# p99 response time
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (application, le)
)
```
**SLO**: p95 < 300ms, p99 < 1s

---

### Availability (Uptime)
```promql
# Fraction of time service health check returns 200
avg_over_time(up{job="asms-services"}[30d]) * 100
```
**SLO**: > 99.9%

---

### Throughput (TPS)
```promql
sum(rate(http_server_requests_seconds_count[1m])) by (application)
```
**SLO**: Sustain > 500 TPS without p95 degrading below 500ms

---

### CPU Utilization
```promql
avg(rate(process_cpu_usage[5m])) by (application) * 100
```
**SLO**: < 70% under normal load (alert at 90%)

---

### Memory Utilization
```promql
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100
```
**SLO**: < 75% heap (alert at 80%)

---

### Cache Hit Rate (Redis)
```promql
redis_keyspace_hits_total / (redis_keyspace_hits_total + redis_keyspace_misses_total) * 100
```
**SLO**: > 80% cache hit rate for amenity/user lookups

---

### Kafka Consumer Lag
```promql
sum(kafka_consumer_group_lag) by (consumergroup, topic)
```
**SLO**: < 1000 messages lag per consumer group (notification freshness)

---

### Payment Success Rate
```promql
sum(rate(payment_completed_total[1h]))
/
sum(rate(payment_initiated_total[1h]))
* 100
```
**SLO**: > 99.5%

---

### Ticket Resolution Rate (Business SLI)
```
resolved_within_sla_tickets / total_tickets_by_priority
```
**SLO**:
- P1: 95% resolved within 4 hours
- P2: 90% resolved within 24 hours
- P3: 85% resolved within 72 hours

---

## 4. Error Budget

```
Monthly Error Budget Calculation:

Availability SLO = 99.9%
Monthly minutes  = 30 × 24 × 60 = 43,200 minutes
Error budget     = 43,200 × (1 - 0.999) = 43.2 minutes/month

If we've burned 30 minutes of downtime this month:
  Remaining budget = 43.2 - 30 = 13.2 minutes

Policy:
  - Error budget > 50% remaining → deploy freely
  - Error budget 10–50% remaining → extra review before deploys
  - Error budget < 10% remaining → freeze non-critical changes
```

---

## 5. Alerting Thresholds Summary

| Alert | Condition | Duration | Severity | Action |
|---|---|---|---|---|
| High CPU | avg CPU > 90% | 5 min | Critical | PagerDuty P1, auto-scale |
| High Memory | Heap > 80% | 5 min | Warning | PagerDuty P2 |
| HTTP 5xx Spike | 5xx count > 100/min | - | Warning | Slack alert |
| High Latency | p95 > 2s | - | Warning | Slack alert |
| Kafka Lag | lag > 1000 | 5 min | Warning | Slack alert |
| Pod Down | pod restarts > 3 in 5min | - | Critical | PagerDuty P1 |
| DB Connection | connection pool > 80% | 2 min | Warning | PagerDuty P2 |
| Payment Failures | success rate < 99% | 5 min | Critical | PagerDuty P1, payment freeze |
