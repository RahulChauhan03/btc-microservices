package com.btc.expenseservice.service.impl;

import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.exception.ExpenseConflictException;
import com.btc.expenseservice.exception.InvalidExpenseException;
import com.btc.expenseservice.repository.ExpenseRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claim locks, called only by claim-service (service token). An expense covered by a claim that is
 * awaiting review or approved stays locked; rejecting or deleting the claim releases it, after which it
 * can be edited or claimed again. Every operation is all-or-nothing within one transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseLockService {

    private final ExpenseRepository expenseRepository;

    /**
     * Makes {@code expenseIds} exactly the set locked by {@code claimId}: locks them (all owned by
     * {@code ownerId}, all on the same trip, none locked by another claim) and releases any others the
     * claim held. Returns the locked expenses, whose amounts can no longer change while locked.
     */
    public List<ExpenseResponseDto> lockForClaim(Long claimId, Long ownerId, List<Long> expenseIds) {
        Set<Long> ids = new LinkedHashSet<>(expenseIds);
        if (ids.size() != expenseIds.size()) {
            throw new InvalidExpenseException("Each expense can be listed only once");
        }

        Map<Long, Expense> expenses = expenseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Expense::getId, Function.identity()));
        for (Long id : ids) {
            Expense expense = expenses.get(id);
            if (expense == null || !ownerId.equals(expense.getOwnerId())) {
                throw new InvalidExpenseException("Expense not found with id: " + id);
            }
        }
        Set<Long> tripIds = expenses.values().stream().map(Expense::getTripId).collect(Collectors.toCollection(HashSet::new));
        if (tripIds.size() > 1) {
            throw new InvalidExpenseException("All expenses in a claim must belong to the same trip");
        }
        List<Long> lockedElsewhere = expenses.values().stream()
                .filter(expense -> expense.getClaimId() != null && !expense.getClaimId().equals(claimId))
                .map(Expense::getId)
                .sorted()
                .toList();
        if (!lockedElsewhere.isEmpty()) {
            throw new ExpenseConflictException("Expenses already included in another claim: " + lockedElsewhere);
        }

        expenseRepository.releaseFromClaimExcept(claimId, ids);
        // The conditional update is the authoritative check: a concurrent claim that locked one of these rows
        // after the reads above makes the count fall short, and this whole transaction rolls back.
        int locked = expenseRepository.lockForClaim(ids, ownerId, claimId);
        if (locked != ids.size()) {
            throw new ExpenseConflictException("Expenses were claimed or changed concurrently; try again");
        }

        return expenseRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Expense::getId))
                .map(ExpenseMapper::toResponse)
                .toList();
    }

    /** Releases every expense locked by the claim. Idempotent. */
    public int releaseClaim(Long claimId) {
        return expenseRepository.releaseFromClaim(claimId);
    }
}
