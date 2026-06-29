# Security Design – ASMS
## Keycloak 24 + Spring Security 6 + Istio mTLS

---

## 1. Security Architecture

```
Client (Browser / Mobile / Postman)
        │
        │ 1. Login Request
        ▼
  ┌─────────────┐
  │  Keycloak   │  ← OAuth2 / OIDC Identity Provider
  │  Realm: ASMS│     Roles: ADMIN, RWA_MEMBER, OWNER, TENANT, ...
  └──────┬──────┘
         │ 2. JWT Access Token (signed, RS256)
         ▼
  ┌─────────────┐
  │ API Gateway │  ← Validates JWT signature (JWKS endpoint from Keycloak)
  │ (Spring     │     Rate limiting, CORS, path-based routing
  │  Cloud GW)  │     Injects X-User-Id, X-User-Role headers downstream
  └──────┬──────┘
         │ 3. Internal HTTP call (mTLS via Istio sidecar)
         ▼
  ┌─────────────┐
  │ Microservice│  ← Trusts gateway-injected headers (no JWT re-validation)
  │             │     Method-level RBAC via @PreAuthorize
  └─────────────┘
```

---

## 2. Keycloak Setup

### Realm Configuration
```
Realm: asms
  ├── Clients
  │    ├── asms-api-gateway   (confidential, client_credentials)
  │    └── asms-frontend      (public, authorization_code + PKCE)
  ├── Roles (Realm Roles)
  │    ├── ADMIN
  │    ├── RWA_MEMBER
  │    ├── OWNER
  │    ├── TENANT
  │    ├── VENDOR
  │    ├── SECURITY
  │    └── SUPPORT_STAFF
  └── User Federation
       └── (optional) LDAP / AD sync
```

### JWT Token Structure
```json
{
  "sub": "usr_01J...",
  "iss": "https://keycloak.asms.io/realms/asms",
  "aud": ["asms-api-gateway"],
  "exp": 1735689600,
  "iat": 1735686000,
  "jti": "uuid",
  "preferred_username": "raj.sharma",
  "email": "raj@example.com",
  "realm_access": {
    "roles": ["OWNER"]
  },
  "resource_access": {
    "asms-api-gateway": {
      "roles": ["OWNER"]
    }
  },
  "society_id": "soc_01J...",
  "unit_id": "unit_01J..."
}
```

---

## 3. API Gateway Security Config

```java
// api-gateway/src/main/java/com/asms/gateway/config/SecurityConfig.java

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(auth -> auth
                .pathMatchers("/api/v1/users/register").permitAll()
                .pathMatchers("/api/v1/vendors/register").permitAll()
                .pathMatchers("/actuator/health/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthConverter() {
        ReactiveJwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
            new ReactiveJwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        ReactiveJwtAuthenticationConverter jwtConverter = new ReactiveJwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtConverter;
    }
}
```

### Gateway JWT Validation (application.yml)
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.asms.io/realms/asms
          jwk-set-uri: https://keycloak.asms.io/realms/asms/protocol/openid-connect/certs
```

### Gateway Header Injection Filter
```java
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
            .cast(JwtAuthenticationToken.class)
            .map(auth -> {
                Jwt jwt = auth.getToken();
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id",      jwt.getSubject())
                    .header("X-User-Role",     extractRole(jwt))
                    .header("X-Society-Id",    jwt.getClaimAsString("society_id"))
                    .build();
                return exchange.mutate().request(mutatedRequest).build();
            })
            .flatMap(chain::filter);
    }

    @Override
    public int getOrder() { return -100; }
}
```

---

## 4. Microservice Security Config

```java
// Each microservice — trusts gateway-injected headers, no Keycloak calls

@Configuration
@EnableMethodSecurity
public class MicroserviceSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new GatewayHeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}

// Filter that converts gateway headers into SecurityContext
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                     FilterChain chain) throws IOException, ServletException {
        String userId  = req.getHeader("X-User-Id");
        String role    = req.getHeader("X-User-Role");

        if (userId != null && role != null) {
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Propagate to MDC for logging
            MDC.put("userId", userId);
            MDC.put("role", role);
        }
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## 5. Method-Level RBAC

```java
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody TicketRequest req,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(201).body(ticketService.create(req, userId));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER')")
    public ResponseEntity<TicketResponse> assignTicket(
            @PathVariable String id,
            @RequestBody AssignTicketRequest req) {
        return ResponseEntity.ok(ticketService.assign(id, req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER', 'SUPPORT_STAFF', 'OWNER', 'TENANT')")
    public ResponseEntity<Page<TicketResponse>> listTickets(
            @RequestParam(required = false) String societyId,
            @RequestParam(required = false) TicketStatus status,
            Pageable pageable,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        // Admin sees all; Owner/Tenant see only their tickets
        return ResponseEntity.ok(ticketService.list(societyId, status, userId, role, pageable));
    }
}
```

---

## 6. Istio mTLS (Zero-Trust Between Pods)

```yaml
# All pods in asms-prod must use mTLS — no plain HTTP between services
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: asms-prod
spec:
  mtls:
    mode: STRICT

---
# Authorization policy — payment-service only callable from amenity-service and billing-service
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: payment-service-policy
  namespace: asms-prod
spec:
  selector:
    matchLabels:
      app: payment-service
  rules:
    - from:
        - source:
            principals:
              - "cluster.local/ns/asms-prod/sa/amenity-service"
              - "cluster.local/ns/asms-prod/sa/billing-service"
              - "cluster.local/ns/asms-prod/sa/api-gateway"
```

---

## 7. Secret Management (AWS Secrets Manager)

```yaml
# No secrets in K8s YAML or environment variables
# Use External Secrets Operator to pull from AWS Secrets Manager

apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: user-service-db-secret
  namespace: asms-prod
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: ClusterSecretStore
  target:
    name: user-service-secret
  data:
    - secretKey: SPRING_DATA_MONGODB_URI
      remoteRef:
        key: asms/prod/user-service
        property: mongodb_uri
    - secretKey: SPRING_KAFKA_PROPERTIES_SSL_KEYSTORE_PASSWORD
      remoteRef:
        key: asms/prod/kafka
        property: keystore_password
```

---

## 8. Input Validation

```java
// DTO with Bean Validation (Jakarta Validation 3)
public record TicketRequest(
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 200, message = "Title must be 5-200 characters")
    String title,

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description max 2000 characters")
    String description,

    @NotNull(message = "Category is required")
    TicketCategory category,

    @NotNull(message = "Priority is required")
    TicketPriority priority
) {}

// Controller — @Valid triggers validation
@PostMapping
public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody TicketRequest req, ...) { ... }
```

---

## 9. Security Headers (API Gateway)

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - AddResponseHeader=X-Content-Type-Options, nosniff
        - AddResponseHeader=X-Frame-Options, DENY
        - AddResponseHeader=X-XSS-Protection, 1; mode=block
        - AddResponseHeader=Strict-Transport-Security, max-age=31536000; includeSubDomains
        - AddResponseHeader=Content-Security-Policy, default-src 'self'
        - RemoveResponseHeader=Server
        - RemoveResponseHeader=X-Powered-By
```

---

## 10. Security Checklist

| Control | Implementation | Status |
|---|---|---|
| Authentication | Keycloak OAuth2 / OIDC, JWT RS256 | Implemented |
| Authorization | Spring Security 6, @PreAuthorize, role-based | Implemented |
| Transport Security | TLS at ALB; Istio mTLS between pods | Implemented |
| Secret Management | AWS Secrets Manager + External Secrets Operator | Implemented |
| Input Validation | Jakarta Validation 3 on all DTOs | Implemented |
| Error Sanitization | Generic error messages; no stack traces in prod | Implemented |
| Rate Limiting | Spring Cloud Gateway rate limiter (Redis) | Implemented |
| Security Headers | HSTS, CSP, X-Frame-Options at gateway | Implemented |
| Audit Logging | All state changes logged with userId + traceId | Implemented |
| Vulnerability Scanning | ECR image scan on push, SonarQube SAST | Implemented |
