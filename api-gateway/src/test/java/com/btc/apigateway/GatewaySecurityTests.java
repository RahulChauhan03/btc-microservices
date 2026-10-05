package com.btc.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTests {

    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    private static final String ISSUER = "btc-flow-test";
    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("btc.security.jwt.secret", () -> SECRET);
        registry.add("btc.security.jwt.issuer", () -> ISSUER);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedRoutesWithoutTokenReturn401() {
        for (String path : new String[] {"/trips", "/expenses", "/claims", "/users", "/users/1", "/payments",
                "/notifications"}) {
            webTestClient.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
        webTestClient.delete().uri("/users/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validTokenPassesAuthentication() {
        // No service instances are registered in tests, so an authenticated request ends in 503 from routing.
        webTestClient.get().uri("/trips")
                .header(HttpHeaders.AUTHORIZATION, bearer(token(SECRET, ISSUER, Instant.now().plusSeconds(600))))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void loginIsPublic() {
        webTestClient.post().uri("/auth/login")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void passwordRecoveryIsPublicButOnlyForPost() {
        // Reaches routing (503: no user-service instance in tests) instead of being stopped with 401.
        for (String path : new String[] {"/auth/forgot-password", "/auth/reset-password"}) {
            webTestClient.post().uri(path).exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            webTestClient.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
        webTestClient.post().uri("/auth/anything-else").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unsignedAlgNoneTokenReturns401() {
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"1\",\"role\":\"ADMIN\",\"iss\":\"" + ISSUER + "\",\"exp\":"
                + Instant.now().plusSeconds(600).getEpochSecond() + "}");
        assertRejected(header + "." + payload + ".signature");
        assertRejected(header + "." + payload + ".");
    }

    @Test
    void modifiedPayloadReturns401() {
        String[] parts = token(SECRET, ISSUER, Instant.now().plusSeconds(600)).split("\\.");
        String tampered = b64("{\"sub\":\"1\",\"role\":\"ADMIN\",\"iss\":\"" + ISSUER + "\",\"exp\":"
                + Instant.now().plusSeconds(600).getEpochSecond() + "}");
        assertRejected(parts[0] + "." + tampered + "." + parts[2]);
    }

    @Test
    void tokenSignedWithOtherKeyReturns401() {
        assertRejected(token(UUID.randomUUID() + "-" + UUID.randomUUID(), ISSUER, Instant.now().plusSeconds(600)));
    }

    @Test
    void expiredTokenReturns401() {
        assertRejected(token(SECRET, ISSUER, Instant.now().minusSeconds(300)));
    }

    @Test
    void wrongIssuerReturns401() {
        assertRejected(token(SECRET, "someone-else", Instant.now().plusSeconds(600)));
    }

    @Test
    void tokenWithoutExpiryReturns401() {
        assertRejected(token(SECRET, ISSUER, null));
    }

    @Test
    void preflightFromConfiguredOriginIsAllowed() {
        webTestClient.options().uri("/trips")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void preflightFromOtherOriginIsRejected() {
        webTestClient.options().uri("/trips")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void unauthorizedResponseCarriesCorsHeadersForBrowserClients() {
        webTestClient.get().uri("/trips")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void sensitiveActuatorEndpointsAreNotReachable() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();

        webTestClient.get().uri("/actuator/gateway/routes").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();

        String token = bearer(token(SECRET, ISSUER, Instant.now().plusSeconds(600)));
        for (String path : new String[] {"/actuator/gateway/routes", "/actuator/env", "/actuator/refresh"}) {
            webTestClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, token)
                    .exchange()
                    .expectStatus().value(status -> assertThat(status).isIn(404, 503));
        }
    }

    @Test
    void internalServiceApisAreNeverRoutedFromOutside() {
        String token = bearer(token(SECRET, ISSUER, Instant.now().plusSeconds(600)));
        webTestClient.put().uri("/expenses/internal/claims/1/locks").header(HttpHeaders.AUTHORIZATION, token)
                .exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/expenses/internal/claims/1/locks").exchange().expectStatus().isUnauthorized();
        webTestClient.post().uri("/notifications/internal/events").header(HttpHeaders.AUTHORIZATION, token)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void totalCountHeaderIsReadableByTheBrowser() {
        webTestClient.get().uri("/trips")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, bearer(token(SECRET, ISSUER, Instant.now().plusSeconds(600))))
                .exchange()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Total-Count");
    }

    private void assertRejected(String token) {
        webTestClient.get().uri("/trips")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private static String token(String secret, String issuer, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("42")
                .issuedAt(Instant.now().minusSeconds(1000))
                .claim("role", "EMPLOYEE");
        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
