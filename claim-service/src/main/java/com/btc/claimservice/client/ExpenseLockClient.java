package com.btc.claimservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** Expense locks held by claims; the lock itself lives in expense-service's database. */
public interface ExpenseLockClient {

    /**
     * Makes {@code expenseIds} exactly the expenses locked by {@code claimId}: all must belong to
     * {@code ownerId}, share one trip and not be locked by another claim. Returns them as locked.
     *
     * @throws com.btc.claimservice.exception.InvalidClaimException      for missing, foreign or mixed-trip expenses
     * @throws com.btc.claimservice.exception.InvalidClaimStateException if an expense is in another claim
     */
    List<ExpenseSummary> lockForClaim(Long claimId, Long ownerId, Collection<Long> expenseIds);

    /**
     * Releases every expense the claim holds (only those; other claims' locks are untouched). Idempotent.
     *
     * @throws com.btc.claimservice.exception.ExpenseServiceRejectedException for a refusal retrying cannot fix
     * @throws com.btc.claimservice.exception.DependencyUnavailableException  for outages and other transient errors
     */
    void releaseClaim(Long claimId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpenseSummary(Long id, Long ownerId, Long tripId, BigDecimal amount) {
    }
}
