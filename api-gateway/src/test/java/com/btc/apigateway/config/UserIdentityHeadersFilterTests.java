package com.btc.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class UserIdentityHeadersFilterTests {

    private final UserIdentityHeadersFilter filter = new UserIdentityHeadersFilter();

    @Test
    void replacesSpoofedIdentityHeadersWithVerifiedClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("42")
                .claim("role", "EMPLOYEE")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        ServerWebExchange exchange = spoofedExchange().mutate()
                .principal(Mono.just(new JwtAuthenticationToken(jwt)))
                .build();

        HttpHeaders forwarded = forwardedHeaders(exchange);

        assertThat(forwarded.get(UserIdentityHeadersFilter.USER_ID_HEADER)).containsExactly("42");
        assertThat(forwarded.get(UserIdentityHeadersFilter.USER_ROLE_HEADER)).containsExactly("EMPLOYEE");
        assertThat(forwarded.containsKey("X-User-Department")).isFalse();
    }

    @Test
    void stripsIdentityHeadersWhenUnauthenticated() {
        HttpHeaders forwarded = forwardedHeaders(spoofedExchange());

        assertThat(forwarded.keySet()).noneMatch(name -> name.toLowerCase().startsWith("x-user-"));
        assertThat(forwarded.getFirst("X-Request-Source")).isEqualTo("web");
    }

    private static MockServerWebExchange spoofedExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/trips")
                .header("X-User-Id", "1")
                .header("x-user-role", "ADMIN")
                .header("X-User-Department", "Finance")
                .header("X-Request-Source", "web"));
    }

    private HttpHeaders forwardedHeaders(ServerWebExchange exchange) {
        AtomicReference<HttpHeaders> captured = new AtomicReference<>();
        GatewayFilterChain chain = forwardedExchange -> {
            captured.set(forwardedExchange.getRequest().getHeaders());
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return captured.get();
    }
}
