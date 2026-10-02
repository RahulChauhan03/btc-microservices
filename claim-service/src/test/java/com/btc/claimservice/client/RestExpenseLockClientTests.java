package com.btc.claimservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.btc.claimservice.client.ExpenseLockClient.ExpenseSummary;
import com.btc.claimservice.exception.DependencyUnavailableException;
import com.btc.claimservice.exception.InvalidClaimException;
import com.btc.claimservice.exception.InvalidClaimStateException;
import com.btc.claimservice.security.ServiceTokenProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The HTTP contract with expense-service's internal lock API. */
class RestExpenseLockClientTests {

    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    private static final String URL = "http://expense-service/expenses/internal/claims/7/locks";

    private MockRestServiceServer server;
    private RestExpenseLockClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestExpenseLockClient(builder, new ServiceTokenProvider(SECRET, "btc-flow"), "http://expense-service");
    }

    @Test
    void locksWithAShortLivedServiceToken() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(request -> authorization.set(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .andExpect(content().json("{\"ownerId\":10,\"expenseIds\":[1]}"))
                .andRespond(withSuccess("[{\"id\":1,\"ownerId\":10,\"tripId\":null,\"amount\":12.50,\"title\":\"x\"}]",
                        MediaType.APPLICATION_JSON));

        List<ExpenseSummary> locked = client.lockForClaim(7L, 10L, List.of(1L));

        assertThat(locked).containsExactly(new ExpenseSummary(1L, 10L, null, new java.math.BigDecimal("12.50")));
        Jwt token = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build()
                .decode(authorization.get().substring("Bearer ".length()));
        assertThat(token.getClaimAsString("role")).isEqualTo("SERVICE");
        assertThat(token.getSubject()).isEqualTo("claim-service");
        assertThat(token.getExpiresAt()).isBefore(token.getIssuedAt().plusSeconds(61));
    }

    @Test
    void refusalsMapToClaimErrorsWithTheDownstreamMessage() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":409,\"message\":\"Expenses already included in another claim: [1]\"}"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":400,\"message\":\"Expense not found with id: 1\"}"));
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.lockForClaim(7L, 10L, List.of(1L)))
                .isInstanceOf(InvalidClaimStateException.class).hasMessage("Expenses already included in another claim: [1]");
        assertThatThrownBy(() -> client.lockForClaim(7L, 10L, List.of(1L)))
                .isInstanceOf(InvalidClaimException.class).hasMessage("Expense not found with id: 1");
        assertThatThrownBy(() -> client.lockForClaim(7L, 10L, List.of(1L)))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void releaseUsesDelete() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());

        client.releaseClaim(7L);
        server.verify();
    }
}
