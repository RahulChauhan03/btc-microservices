package com.btc.claimservice.reimbursement;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of the payment record for an approved claim. PAID is final. BTC Flow only records what finance
 * reports; it does not move money.
 */
public enum ReimbursementStatus {
    PENDING,
    PROCESSING,
    FAILED,
    PAID;

    private static final Map<ReimbursementStatus, Set<ReimbursementStatus>> NEXT = Map.of(
            PENDING, EnumSet.of(PROCESSING, PAID),
            PROCESSING, EnumSet.of(PAID, FAILED),
            FAILED, EnumSet.of(PROCESSING),
            PAID, EnumSet.noneOf(ReimbursementStatus.class));

    public boolean canMoveTo(ReimbursementStatus next) {
        return NEXT.get(this).contains(next);
    }

    public Set<ReimbursementStatus> next() {
        return NEXT.get(this);
    }
}
