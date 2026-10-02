package com.btc.expenseservice.client;

import com.btc.expenseservice.exception.DependencyUnavailableException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls trip-service with the caller's own JWT, so trip-service applies its ownership rules too. */
@Component
public class RestTripClient implements TripClient {

    private final RestClient restClient;

    public RestTripClient(RestClient.Builder loadBalancedRestClientBuilder,
                          @Value("${btc.services.trip-service-url:http://trip-service}") String tripServiceUrl) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(tripServiceUrl).build();
    }

    @Override
    public Optional<TripSummary> findTrip(Long tripId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/trips/{id}", tripId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken())
                    .retrieve()
                    .body(TripSummary.class));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new DependencyUnavailableException("Trip service is unavailable", exception);
        }
    }

    private static String callerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getTokenValue();
        }
        throw new IllegalStateException("No authenticated caller to act on behalf of");
    }
}
