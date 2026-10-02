package com.btc.claimservice.service.impl;

import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.client.ExpenseLockClient.ExpenseSummary;
import com.btc.claimservice.dto.ClaimRequestDto;
import com.btc.claimservice.dto.ClaimResponseDto;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.entity.ClaimStatus;
import com.btc.claimservice.exception.ClaimNotFoundException;
import com.btc.claimservice.exception.DuplicateClaimException;
import com.btc.claimservice.exception.InvalidClaimException;
import com.btc.claimservice.exception.InvalidClaimStateException;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.CurrentUser;
import com.btc.claimservice.service.ClaimService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Claim rules:
 * - A claim covers one or more of the caller's own expenses, all from the same trip (or all without a trip).
 *   Its expenses are locked in expense-service while the claim is awaiting review or approved, so they cannot
 *   be edited, deleted or included in another claim; that lock is a single conditional UPDATE in the expense
 *   database, so concurrent claims for the same expense cannot both succeed.
 * - Rejecting or deleting a claim releases its expenses for future claims; approval keeps them locked.
 * - The amount is the sum of the locked expenses; status, owner and reviewer are set by the server only.
 * - Owners may edit or delete a claim only while it awaits review.
 * - Only an administrator who does not own the claim may approve or reject it; both outcomes are final.
 * - Users see their own claims; administrators can read all claims.
 *
 * Cross-service consistency fails closed: if this transaction rolls back after locking, the lock is undone;
 * locks are released only after a delete/reject has committed. A failed release leaves expenses locked
 * (never unlocked under a live claim) and is logged for manual release.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClaimServiceImpl implements ClaimService {

    private static final Long NO_CLAIM = -1L;

    private final ClaimRepository claimRepository;
    private final ExpenseLockClient expenseLockClient;

    @Override
    public ClaimResponseDto createClaim(ClaimRequestDto requestDto, CurrentUser actor) {
        if (claimRepository.existsByClaimNumber(requestDto.getClaimNumber())) {
            throw new DuplicateClaimException("Claim already exists with claim number: " + requestDto.getClaimNumber());
        }
        Set<Long> expenseIds = distinctExpenseIds(requestDto.getExpenseIds(), NO_CLAIM);

        Claim claim = claimRepository.saveAndFlush(Claim.builder()
                .claimNumber(requestDto.getClaimNumber())
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .claimAmount(BigDecimal.ZERO)
                .status(ClaimStatus.SUBMITTED.name())
                .ownerId(actor.id())
                .build());
        Long claimId = claim.getId();
        onRollback(() -> expenseLockClient.releaseClaim(claimId), "Releasing expenses of rolled-back claim " + claimId);

        applyLockedExpenses(claim, expenseLockClient.lockForClaim(claimId, actor.id(), expenseIds), expenseIds, actor);
        return mapToResponse(claimRepository.save(claim));
    }

    @Override
    @Transactional(readOnly = true)
    public ClaimResponseDto getClaimById(Long id, CurrentUser actor) {
        Claim claim = findClaimById(id);
        if (!actor.admin() && !actor.owns(claim.getOwnerId())) {
            throw notFound(id);
        }
        return mapToResponse(claim);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClaimResponseDto> getAllClaims(CurrentUser actor, Pageable pageable) {
        Page<Claim> claims = actor.admin()
                ? claimRepository.findAll(pageable)
                : claimRepository.findAllByOwnerId(actor.id(), pageable);
        return claims.map(this::mapToResponse);
    }

    @Override
    public ClaimResponseDto updateClaim(Long id, ClaimRequestDto requestDto, CurrentUser actor) {
        Claim existingClaim = findOwnedClaim(id, actor);
        requireAwaitingReview(existingClaim);

        if (claimRepository.existsByClaimNumberAndIdNot(requestDto.getClaimNumber(), id)) {
            throw new DuplicateClaimException("Claim already exists with claim number: " + requestDto.getClaimNumber());
        }
        Set<Long> expenseIds = distinctExpenseIds(requestDto.getExpenseIds(), id);

        Set<Long> previousIds = Set.copyOf(existingClaim.getExpenseIds());
        onRollback(() -> {
            if (previousIds.isEmpty()) {
                expenseLockClient.releaseClaim(id);
            } else {
                expenseLockClient.lockForClaim(id, actor.id(), previousIds);
            }
        }, "Restoring expense locks of claim " + id);

        List<ExpenseSummary> locked = expenseLockClient.lockForClaim(id, actor.id(), expenseIds);
        existingClaim.setClaimNumber(requestDto.getClaimNumber());
        existingClaim.setTitle(requestDto.getTitle());
        existingClaim.setDescription(requestDto.getDescription());
        applyLockedExpenses(existingClaim, locked, expenseIds, actor);

        return mapToResponse(claimRepository.save(existingClaim));
    }

    @Override
    public void deleteClaim(Long id, CurrentUser actor) {
        Claim existingClaim = findOwnedClaim(id, actor);
        requireAwaitingReview(existingClaim);
        claimRepository.delete(existingClaim);
        afterCommit(() -> expenseLockClient.releaseClaim(id), "Releasing expenses of deleted claim " + id);
    }

    @Override
    public ClaimResponseDto approveClaim(Long id, CurrentUser actor) {
        return review(id, actor, ClaimStatus.APPROVED);
    }

    @Override
    public ClaimResponseDto rejectClaim(Long id, CurrentUser actor) {
        ClaimResponseDto rejected = review(id, actor, ClaimStatus.REJECTED);
        afterCommit(() -> expenseLockClient.releaseClaim(id), "Releasing expenses of rejected claim " + id);
        return rejected;
    }

    private ClaimResponseDto review(Long id, CurrentUser actor, ClaimStatus outcome) {
        if (!actor.admin()) {
            throw new AccessDeniedException("Administrator role required to review claims");
        }
        Claim claim = findClaimById(id);
        if (actor.owns(claim.getOwnerId())) {
            throw new AccessDeniedException("You cannot review your own claim");
        }
        requireAwaitingReview(claim);
        if (claim.getExpenseIds().isEmpty()) {
            throw new InvalidClaimStateException(
                    "Claim has no linked expenses, so its amount was not calculated by the server; reconcile it before review");
        }

        claim.setStatus(outcome.name());
        claim.setReviewedBy(actor.id());
        claim.setReviewedAt(LocalDateTime.now());
        return mapToResponse(claimRepository.save(claim));
    }

    private Set<Long> distinctExpenseIds(List<Long> requestedIds, Long claimId) {
        Set<Long> ids = new LinkedHashSet<>(requestedIds);
        if (ids.size() != requestedIds.size()) {
            throw new InvalidClaimException("Each expense can be listed only once");
        }
        // Fast local check; the authoritative one is the conditional lock in expense-service.
        List<Long> alreadyClaimed = claimRepository.findExpenseIdsInActiveClaims(ids, claimId);
        if (!alreadyClaimed.isEmpty()) {
            throw new InvalidClaimStateException("Expenses already included in another claim: " + alreadyClaimed);
        }
        return ids;
    }

    /** Re-checks what expense-service locked (defence in depth) and derives amount and trip from it. */
    private void applyLockedExpenses(Claim claim, List<ExpenseSummary> locked, Set<Long> requestedIds, CurrentUser actor) {
        Set<Long> lockedIds = locked.stream().map(ExpenseSummary::id).collect(Collectors.toSet());
        boolean consistent = lockedIds.equals(requestedIds)
                && locked.stream().allMatch(expense -> actor.owns(expense.ownerId()) && expense.amount() != null);
        if (!consistent) {
            throw new InvalidClaimException("Expense service returned an inconsistent lock result");
        }
        Set<Long> tripIds = locked.stream().map(ExpenseSummary::tripId).collect(Collectors.toCollection(HashSet::new));
        if (tripIds.size() != 1) {
            throw new InvalidClaimException("All expenses in a claim must belong to the same trip");
        }

        claim.setClaimAmount(locked.stream().map(ExpenseSummary::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        claim.setTripId(tripIds.iterator().next());
        claim.getExpenseIds().clear();
        claim.getExpenseIds().addAll(requestedIds);
    }

    private void requireAwaitingReview(Claim claim) {
        boolean awaitingReview = ClaimStatus.parse(claim.getStatus()).map(ClaimStatus::awaitingReview).orElse(false);
        if (!awaitingReview) {
            throw new InvalidClaimStateException("Claim is " + claim.getStatus() + " and can no longer be changed");
        }
    }

    private Claim findOwnedClaim(Long id, CurrentUser actor) {
        Claim claim = findClaimById(id);
        if (!actor.owns(claim.getOwnerId())) {
            if (actor.admin()) {
                throw new AccessDeniedException("Administrators can review but not modify other users' claims");
            }
            throw notFound(id);
        }
        return claim;
    }

    private Claim findClaimById(Long id) {
        return claimRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private ClaimNotFoundException notFound(Long id) {
        return new ClaimNotFoundException("Claim not found with id: " + id);
    }

    /** Runs once the surrounding transaction has committed (immediately when there is none, e.g. unit tests). */
    private static void afterCommit(Runnable action, String description) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runLoggingFailure(action, description);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runLoggingFailure(action, description);
            }
        });
    }

    /** Compensation that runs only if the surrounding transaction rolls back. */
    private static void onRollback(Runnable action, String description) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    runLoggingFailure(action, description);
                }
            }
        });
    }

    private static void runLoggingFailure(Runnable action, String description) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error("{} failed; the expenses stay locked until released manually "
                    + "(see docs/migrations/phase-4-flyway.md, 'Releasing a stuck expense lock')", description, exception);
        }
    }

    private ClaimResponseDto mapToResponse(Claim claim) {
        return ClaimResponseDto.builder()
                .id(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .title(claim.getTitle())
                .description(claim.getDescription())
                .claimAmount(claim.getClaimAmount())
                .status(claim.getStatus())
                .submittedAt(claim.getSubmittedAt())
                .ownerId(claim.getOwnerId())
                .tripId(claim.getTripId())
                .expenseIds(claim.getExpenseIds().stream().sorted().toList())
                .reviewedBy(claim.getReviewedBy())
                .reviewedAt(claim.getReviewedAt())
                .build();
    }
}
