package com.btc.notificationservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Mints real HS256 tokens with a per-run random key, for web tests that go through JWT validation. */
public final class TestJwt {

    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    private static final String ISSUER = "btc-flow-test";

    private TestJwt() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("btc.security.jwt.secret", () -> SECRET);
        registry.add("btc.security.jwt.issuer", () -> ISSUER);
    }

    /** A claim-service style token: role SERVICE and a non-numeric subject. */
    public static String service() {
        return sign(JwtClaimsSet.builder().issuer(ISSUER).subject("claim-service").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claim("role", "SERVICE").build());
    }

    public static String bearer(long userId, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("role", role)
                .build();
        return sign(claims);
    }

    private static String sign(JwtClaimsSet claims) {
        String token = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return "Bearer " + token;
    }
}
