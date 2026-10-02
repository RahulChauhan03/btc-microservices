package com.btc.claimservice.outbox;

/** PENDING (waiting, possibly retrying) -> IN_PROGRESS (leased by a worker) -> COMPLETED, or FAILED for manual recovery. */
public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
