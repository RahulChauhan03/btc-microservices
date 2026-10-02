package com.btc.claimservice.client;

import com.btc.claimservice.exception.DependencyUnavailableException;
import com.btc.claimservice.exception.ExpenseServiceRejectedException;
import com.btc.claimservice.exception.InvalidClaimException;
import com.btc.claimservice.exception.InvalidClaimStateException;
import com.btc.claimservice.security.ServiceTokenProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls expense-service's internal lock API with a claim-service SERVICE token. */
@Component
public class RestExpenseLockClient implements ExpenseLockClient {

    private static final String LOCKS_PATH = "/expenses/internal/claims/{claimId}/locks";
    /** 401 can be a secret rotation still in progress; 408/429 are transient by definition. */
    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(401, 408, 429);

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public RestExpenseLockClient(RestClient.Builder loadBalancedRestClientBuilder,
                                 ServiceTokenProvider serviceTokenProvider,
                                 @Value("${btc.services.expense-service-url:http://expense-service}") String expenseServiceUrl) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(expenseServiceUrl).build();
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Override
    public List<ExpenseSummary> lockForClaim(Long claimId, Long ownerId, Collection<Long> expenseIds) {
        try {
            List<ExpenseSummary> locked = restClient.put()
                    .uri(LOCKS_PATH, claimId)
                    .header(HttpHeaders.AUTHORIZATION, serviceTokenProvider.bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ownerId", ownerId, "expenseIds", List.copyOf(expenseIds)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ExpenseSummary>>() { });
            return locked == null ? List.of() : locked;
        } catch (HttpClientErrorException exception) {
            String message = messageOf(exception);
            if (exception.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new InvalidClaimException(message);
            }
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                throw new InvalidClaimStateException(message);
            }
            throw new DependencyUnavailableException("Expense service rejected the lock request", exception);
        } catch (RestClientException exception) {
            throw new DependencyUnavailableException("Expense service is unavailable", exception);
        }
    }

    @Override
    public void releaseClaim(Long claimId) {
        try {
            restClient.delete()
                    .uri(LOCKS_PATH, claimId)
                    .header(HttpHeaders.AUTHORIZATION, serviceTokenProvider.bearerToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            if (RETRYABLE_CLIENT_ERRORS.contains(exception.getStatusCode().value())) {
                throw new DependencyUnavailableException("Expense service is temporarily refusing requests", exception);
            }
            throw new ExpenseServiceRejectedException("Expense service refused the release: HTTP "
                    + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new DependencyUnavailableException("Expense service is unavailable", exception);
        }
    }

    private static String messageOf(HttpClientErrorException exception) {
        try {
            ErrorBody body = exception.getResponseBodyAs(ErrorBody.class);
            if (body != null && body.message() != null) {
                return body.message();
            }
        } catch (RuntimeException ignored) {
            // fall through to a generic message
        }
        return "Expense service refused the request";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(String message) {
    }
}
