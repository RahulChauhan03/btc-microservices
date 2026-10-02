package com.btc.tripservice.client;

import com.btc.tripservice.exception.DependencyUnavailableException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls expense-service with the caller's own JWT. Any failure is reported, never treated as "no expenses". */
@Component
public class RestTripExpensesClient implements TripExpensesClient {

    private final RestClient restClient;

    public RestTripExpensesClient(RestClient.Builder loadBalancedRestClientBuilder,
                                  @Value("${btc.services.expense-service-url:http://expense-service}") String expenseServiceUrl) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(expenseServiceUrl).build();
    }

    @Override
    public boolean hasExpenses(Long tripId) {
        try {
            ResponseEntity<List> response = restClient.get()
                    .uri("/expenses?tripId={tripId}&size=1", tripId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken())
                    .retrieve()
                    .toEntity(List.class);
            return response.getBody() != null && !response.getBody().isEmpty();
        } catch (RestClientException exception) {
            throw new DependencyUnavailableException("Expense service is unavailable", exception);
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
