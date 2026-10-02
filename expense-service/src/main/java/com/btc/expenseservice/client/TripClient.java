package com.btc.expenseservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;

public interface TripClient {

    /** Looks the trip up as the calling user; empty when it does not exist or the caller may not see it. */
    Optional<TripSummary> findTrip(Long tripId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TripSummary(Long id, Long ownerId) {
    }
}
