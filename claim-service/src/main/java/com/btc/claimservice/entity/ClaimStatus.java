package com.btc.claimservice.entity;

import java.util.Optional;

/**
 * Claim lifecycle. New claims start as SUBMITTED. PENDING is a legacy value that is treated the same way.
 * SUBMITTED/PENDING -> APPROVED or REJECTED, by an administrator who is not the claim owner.
 * APPROVED and REJECTED are final.
 */
public enum ClaimStatus {
    PENDING,
    SUBMITTED,
    APPROVED,
    REJECTED;

    public boolean awaitingReview() {
        return this == PENDING || this == SUBMITTED;
    }

    public static Optional<ClaimStatus> parse(String value) {
        try {
            return Optional.of(valueOf(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }
}
