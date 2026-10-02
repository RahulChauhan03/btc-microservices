package com.btc.claimservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Short-lived token identifying claim-service itself (role SERVICE) for internal calls such as expense locks.
 * Its subject is not a user id, so it cannot be used on any user-facing endpoint.
 */
@Component
public class ServiceTokenProvider {

    private static final long TTL_SECONDS = 60;
    private static final String SERVICE_NAME = "claim-service";

    private final JwtEncoder encoder;
    private final String issuer;

    public ServiceTokenProvider(@Value("${btc.security.jwt.secret}") String secret,
                                @Value("${btc.security.jwt.issuer}") String issuer) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        this.issuer = issuer;
    }

    public String bearerToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(SERVICE_NAME)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TTL_SECONDS))
                .claim("role", "SERVICE")
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
