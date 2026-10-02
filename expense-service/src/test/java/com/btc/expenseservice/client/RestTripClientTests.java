package com.btc.expenseservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.btc.expenseservice.exception.DependencyUnavailableException;
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

/** The HTTP contract with trip-service: caller's token relayed; 404 means "no such trip", errors are reported. */
class RestTripClientTests {

    private MockRestServiceServer server;
    private RestTripClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestTripClient(builder, "http://trip-service");
        Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "HS256").subject("10")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void relaysCallerTokenAndMapsResponses() {
        server.expect(requestTo("http://trip-service/trips/5"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withSuccess("{\"id\":5,\"ownerId\":10,\"destination\":\"Goa\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://trip-service/trips/6")).andRespond(withResourceNotFound());

        assertThat(client.findTrip(5L)).contains(new TripClient.TripSummary(5L, 10L));
        assertThat(client.findTrip(6L)).isEmpty();
    }

    @Test
    void serverErrorIsReportedAsUnavailable() {
        server.expect(requestTo("http://trip-service/trips/5")).andRespond(withServerError());

        assertThatThrownBy(() -> client.findTrip(5L)).isInstanceOf(DependencyUnavailableException.class);
    }
}
