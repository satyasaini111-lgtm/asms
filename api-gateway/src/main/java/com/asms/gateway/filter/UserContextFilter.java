package com.asms.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    private static final Set<String> ASMS_ROLES = Set.of(
            "ADMIN", "RWA_MEMBER", "OWNER", "TENANT", "VENDOR", "SECURITY", "SUPPORT_STAFF");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    Jwt jwt = auth.getToken();
                    String role = extractRole(jwt);
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", jwt.getSubject())
                            .header("X-User-Role", role)
                            .header("X-Society-Id", jwt.getClaimAsString("society_id"))
                            .header("X-Unit-Id", jwt.getClaimAsString("unit_id"))
                            .build();
                    return exchange.mutate().request(mutatedRequest).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private String extractRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return "UNKNOWN";
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null || roles.isEmpty()) return "UNKNOWN";
        // Pick first ASMS-specific role; Keycloak injects default roles first
        return roles.stream()
                .filter(ASMS_ROLES::contains)
                .findFirst()
                .orElse("UNKNOWN");
    }

    @Override
    public int getOrder() { return -100; }
}
