package com.btc.claimservice.outbox;

/** Release types (why a claim's expense locks must be released) and NOTIFICATION. */
public enum OutboxEventType {
    CLAIM_REJECTED,
    CLAIM_DELETED,
    /** A claim creation locked expenses and then rolled back; the claim row never committed. */
    CLAIM_CREATION_ROLLED_BACK,
    /** Not a release: delivers the JSON payload to notification-service. */
    NOTIFICATION;

    public boolean isRelease() {
        return this != NOTIFICATION;
    }
}
