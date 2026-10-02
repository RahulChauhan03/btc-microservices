package com.btc.claimservice.outbox;

/** Why a claim's expense locks must be released; every type is delivered as the same idempotent release. */
public enum OutboxEventType {
    CLAIM_REJECTED,
    CLAIM_DELETED,
    /** A claim creation locked expenses and then rolled back; the claim row never committed. */
    CLAIM_CREATION_ROLLED_BACK
}
