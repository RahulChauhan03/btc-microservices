package com.btc.tripservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.btc.tripservice.exception.DependencyUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The HTTP contract with expense-service: caller's token relayed, failures never read as "no expenses". */
class RestTripExpensesClientTests {

    private MockRestServiceServer server;
    private RestTripExpensesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestTripExpensesClient(builder, "http://expense-service");
        Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "HS256").subject("10")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void relaysCallerTokenAndDetectsExpenses() {
        server.expect(requestTo("http://expense-service/expenses?tripId=5&size=1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withSuccess("[{\"id\":1}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://expense-service/expenses?tripId=6&size=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.hasExpenses(5L)).isTrue();
        assertThat(client.hasExpenses(6L)).isFalse();
        server.verify();
    }

    @Test
    void failureIsReportedNotTreatedAsEmpty() {
        server.expect(requestTo("http://expense-service/expenses?tripId=5&size=1")).andRespond(withServiceUnavailable());

        assertThatThrownBy(() -> client.hasExpenses(5L)).isInstanceOf(DependencyUnavailableException.class);
    }
}
