package com.btc.claimservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.btc.claimservice.exception.DependencyUnavailableException;
import com.btc.claimservice.exception.PermanentDeliveryException;
import com.btc.claimservice.security.ServiceTokenProvider;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The HTTP contract with notification-service's internal event intake. */
class RestNotificationClientTests {

    private static final String URL = "http://notification-service/notifications/internal/events";
    private static final NotificationMessage MESSAGE =
            NotificationMessage.toUser(10L, 1L, "CLAIM_APPROVED", "Claim approved", "Approved.", "/claims");

    private MockRestServiceServer server;
    private RestNotificationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestNotificationClient(builder,
                new ServiceTokenProvider(UUID.randomUUID() + "-" + UUID.randomUUID(), "btc-flow"), "http://notification-service");
    }

    @Test
    void postsTheEventWithAServiceToken() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(request -> authorization.set(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .andExpect(content().json("""
                        {"eventId":"e-1","audience":"USER","recipientId":10,"actorId":1,"type":"CLAIM_APPROVED",
                         "title":"Claim approved","message":"Approved.","link":"/claims"}"""))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.publish("e-1", MESSAGE);

        server.verify();
        assertThat(authorization.get()).startsWith("Bearer ");
    }

    @Test
    void classifiesFailuresForTheOutbox() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.publish("e-1", MESSAGE)).isInstanceOf(PermanentDeliveryException.class);
        assertThatThrownBy(() -> client.publish("e-1", MESSAGE)).isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> client.publish("e-1", MESSAGE)).isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }
}
