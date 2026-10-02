package com.btc.tripservice.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.tripservice.controller.TripController;
import com.btc.tripservice.service.TripService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Direct calls to trip-service (bypassing the gateway) must still present a valid JWT. */
@WebMvcTest(controllers = TripController.class)
@Import(SecurityConfig.class)
class TripServiceSecurityTests {

    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    private static final String ISSUER = "btc-flow-test";

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("btc.security.jwt.secret", () -> SECRET);
        registry.add("btc.security.jwt.issuer", () -> ISSUER);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripService tripService;

    @Test
    void requestsWithoutValidTokenAreRejected() throws Exception {
        mockMvc.perform(get("/trips")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/trips/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/trips").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, Instant.now().minusSeconds(300))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/trips").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(UUID.randomUUID() + "-" + UUID.randomUUID(), Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        when(tripService.getAllTrips()).thenReturn(List.of());

        mockMvc.perform(get("/trips").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, Instant.now().plusSeconds(600))))
                .andExpect(status().isOk());
    }

    private static String token(String secret, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("1")
                .issuedAt(Instant.now().minusSeconds(1000))
                .expiresAt(expiresAt)
                .claim("role", "EMPLOYEE")
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
