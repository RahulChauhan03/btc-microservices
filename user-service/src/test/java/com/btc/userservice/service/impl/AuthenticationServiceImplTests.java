package com.btc.userservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.btc.userservice.config.JwtConfig;
import com.btc.userservice.dto.AuthLoginRequestDto;
import com.btc.userservice.dto.AuthResponseDto;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.InvalidCredentialsException;
import com.btc.userservice.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthenticationServiceImplTests {

    private static final String ISSUER = "btc-flow-test";
    private static final String PASSWORD = UUID.randomUUID().toString();

    private final SecretKey key = JwtConfig.hmacKey(UUID.randomUUID() + "-" + UUID.randomUUID());
    private final JwtDecoder decoder = JwtConfig.decoder(key, ISSUER);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationServiceImpl(userRepository, passwordEncoder,
                new NimbusJwtEncoder(new ImmutableSecret<>(key)));
        ReflectionTestUtils.setField(authenticationService, "issuer", ISSUER);
        User user = User.builder()
                .id(7L)
                .name("Asha \"Quote\" Rao")
                .email("asha@example.com")
                .phone("1")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role("ADMIN")
                .build();
        when(userRepository.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void loginIssuesSignedTokenWithRequiredClaimsAndKeepsResponseContract() {
        AuthResponseDto response = authenticationService.login(new AuthLoginRequestDto("asha@example.com", PASSWORD));

        assertThat(response.getExpiresIn()).isEqualTo(8 * 60 * 60L);
        assertThat(response.getUser().getId()).isEqualTo(7L);
        assertThat(response.getUser().getRole()).isEqualTo("ADMIN");

        Jwt jwt = decoder.decode(response.getToken());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Asha \"Quote\" Rao");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getExpiresAt()).isBetween(Instant.now().plusSeconds(8 * 3600 - 60),
                Instant.now().plusSeconds(8 * 3600 + 60));
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThatThrownBy(() -> authenticationService.login(new AuthLoginRequestDto("asha@example.com", "wrong-pass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void decoderRejectsModifiedPayload() {
        String[] parts = authenticationService.login(new AuthLoginRequestDto("asha@example.com", PASSWORD))
                .getToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"sub\":\"7\"", "\"sub\":\"1\"");
        String tampered = parts[0] + "." + b64(payload) + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsUnsignedOtherKeyExpiredAndWrongIssuerTokens() {
        String unsigned = b64("{\"alg\":\"none\"}") + "." + b64("{\"sub\":\"1\",\"iss\":\"" + ISSUER + "\",\"exp\":"
                + Instant.now().plusSeconds(600).getEpochSecond() + "}") + ".";
        assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);

        SecretKey otherKey = JwtConfig.hmacKey(UUID.randomUUID() + "-" + UUID.randomUUID());
        assertThatThrownBy(() -> decoder.decode(sign(otherKey, ISSUER, Instant.now().plusSeconds(600))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(key, ISSUER, Instant.now().minusSeconds(300))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign(key, "other-issuer", Instant.now().plusSeconds(600))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretsAreRefused() {
        assertThatThrownBy(() -> JwtConfig.hmacKey("too-short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtConfig.hmacKey(null)).isInstanceOf(IllegalStateException.class);
    }

    private static String sign(SecretKey key, String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("1")
                .issuedAt(Instant.now().minusSeconds(1000))
                .expiresAt(expiresAt)
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
