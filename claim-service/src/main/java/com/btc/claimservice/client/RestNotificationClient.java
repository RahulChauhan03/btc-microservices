package com.btc.claimservice.client;

import com.btc.claimservice.exception.DependencyUnavailableException;
import com.btc.claimservice.exception.PermanentDeliveryException;
import com.btc.claimservice.security.ServiceTokenProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls notification-service's internal event intake with a claim-service SERVICE token. */
@Component
public class RestNotificationClient implements NotificationClient {

    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(401, 408, 429);

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public RestNotificationClient(RestClient.Builder loadBalancedRestClientBuilder, ServiceTokenProvider serviceTokenProvider,
                                  @Value("${btc.services.notification-service-url:http://notification-service}") String baseUrl) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Override
    public void publish(String eventId, NotificationMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("audience", message.audience());
        body.put("recipientId", message.recipientId());
        body.put("actorId", message.actorId());
        body.put("type", message.type());
        body.put("title", message.title());
        body.put("message", message.message());
        body.put("link", message.link());
        try {
            restClient.post()
                    .uri("/notifications/internal/events")
                    .header(HttpHeaders.AUTHORIZATION, serviceTokenProvider.bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            if (RETRYABLE_CLIENT_ERRORS.contains(exception.getStatusCode().value())) {
                throw new DependencyUnavailableException("Notification service is temporarily refusing requests", exception);
            }
            throw new PermanentDeliveryException("Notification service refused the event: HTTP "
                    + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new DependencyUnavailableException("Notification service is unavailable", exception);
        }
    }
}
