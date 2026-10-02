package com.btc.apigateway.config;

import java.util.List;
import java.util.Locale;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Removes any client-supplied X-User-* headers and forwards the identity taken from the verified JWT,
 * so downstream services never trust identity headers the caller made up.
 */
@Component
public class UserIdentityHeadersFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String IDENTITY_HEADER_PREFIX = "x-user-";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> ((JwtAuthenticationToken) principal).getToken())
                .map(jwt -> withIdentity(exchange, jwt))
                .switchIfEmpty(Mono.fromSupplier(() -> withIdentity(exchange, null)))
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            List<String> spoofed = headers.keySet().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(IDENTITY_HEADER_PREFIX))
                    .toList();
            spoofed.forEach(headers::remove);
            if (jwt != null) {
                headers.set(USER_ID_HEADER, jwt.getSubject());
                String role = jwt.getClaimAsString("role");
                if (role != null) {
                    headers.set(USER_ROLE_HEADER, role);
                }
            }
        }).build();
        return exchange.mutate().request(request).build();
    }
}
