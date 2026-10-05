package com.btc.claimservice.client;

/** Delivers notification events to notification-service. */
public interface NotificationClient {

    /**
     * Idempotent per eventId (notification-service stores each event once).
     *
     * @throws com.btc.claimservice.exception.PermanentDeliveryException     for a refusal retrying cannot fix
     * @throws com.btc.claimservice.exception.DependencyUnavailableException for outages and other transient errors
     */
    void publish(String eventId, NotificationMessage message);
}
