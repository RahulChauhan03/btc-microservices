package com.btc.userservice.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.userservice.controller.AuthController;
import com.btc.userservice.controller.UserController;
import com.btc.userservice.dto.AuthResponseDto;
import com.btc.userservice.service.AuthenticationService;
import com.btc.userservice.service.UserService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Direct calls to user-service (bypassing the gateway) must still present a valid JWT. */
@WebMvcTest(controllers = {UserController.class, AuthController.class})
@Import({SecurityConfig.class, JwtConfig.class})
class UserServiceSecurityTests {

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
    private UserService userService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    void userEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        when(userService.getAllUsers(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsPublic() throws Exception {
        when(authenticationService.login(any())).thenReturn(AuthResponseDto.builder().token("t").build());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"irrelevant-pass\"}"))
                .andExpect(status().isOk());
    }

    private static String validToken() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("role", "ADMIN")
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(JwtConfig.hmacKey(SECRET)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
