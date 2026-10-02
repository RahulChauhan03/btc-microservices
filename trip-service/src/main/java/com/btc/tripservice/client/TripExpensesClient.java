package com.btc.tripservice.client;

public interface TripExpensesClient {

    /** Whether any expense references the trip. Asked as the calling user, who owns every expense on their trip. */
    boolean hasExpenses(Long tripId);
}
