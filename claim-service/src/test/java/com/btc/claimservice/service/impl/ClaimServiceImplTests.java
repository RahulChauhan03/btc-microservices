package com.btc.claimservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.client.ExpenseLockClient.ExpenseSummary;
import com.btc.claimservice.dto.ClaimRequestDto;
import com.btc.claimservice.dto.ClaimResponseDto;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.exception.ClaimNotFoundException;
import com.btc.claimservice.exception.InvalidClaimException;
import com.btc.claimservice.exception.InvalidClaimStateException;
import com.btc.claimservice.outbox.ClaimOutbox;
import com.btc.claimservice.outbox.OutboxEventType;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.CurrentUser;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

/** Business rules in isolation; lock releases are recorded in the (mocked) claim outbox. */
class ClaimServiceImplTests {

    private static final CurrentUser OWNER = new CurrentUser(10L, false);
    private static final CurrentUser OTHER = new CurrentUser(20L, false);
    private static final CurrentUser ADMIN = new CurrentUser(1L, true);
    private static final long NEW_CLAIM_ID = 50L;

    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final ExpenseLockClient expenseLockClient = mock(ExpenseLockClient.class);
    private final ClaimOutbox claimOutbox = mock(ClaimOutbox.class);
    private final ClaimServiceImpl claimService = new ClaimServiceImpl(claimRepository, expenseLockClient, claimOutbox);

    @BeforeEach
    void setUp() {
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimRepository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim claim = invocation.getArgument(0);
            claim.setId(NEW_CLAIM_ID);
            return claim;
        });
        when(claimRepository.findExpenseIdsInActiveClaims(anyCollection(), anyLong())).thenReturn(List.of());
    }

    @Test
    void amountTripStatusAndOwnerComeFromLockedExpenses() {
        locks(NEW_CLAIM_ID, summary(1L, OWNER.id(), 100L, "40.25"), summary(2L, OWNER.id(), 100L, "9.75"));

        ClaimResponseDto created = claimService.createClaim(request(1L, 2L), OWNER);

        assertThat(created.getClaimAmount()).isEqualByComparingTo("50.00");
        assertThat(created.getStatus()).isEqualTo("SUBMITTED");
        assertThat(created.getOwnerId()).isEqualTo(OWNER.id());
        assertThat(created.getTripId()).isEqualTo(100L);
        assertThat(created.getExpenseIds()).containsExactly(1L, 2L);
        verify(expenseLockClient).lockForClaim(NEW_CLAIM_ID, OWNER.id(), Set.of(1L, 2L));
    }

    @Test
    void lockRefusalsPropagate() {
        when(expenseLockClient.lockForClaim(anyLong(), anyLong(), anyCollection()))
                .thenThrow(new InvalidClaimException("Expense not found with id: 4"))
                .thenThrow(new InvalidClaimStateException("Expenses already included in another claim: [1]"));

        assertThatThrownBy(() -> claimService.createClaim(request(4L), OWNER)).isInstanceOf(InvalidClaimException.class);
        assertThatThrownBy(() -> claimService.createClaim(request(1L), OWNER))
                .isInstanceOf(InvalidClaimStateException.class);
    }

    @Test
    void inconsistentLockResultsAreRejected() {
        locks(NEW_CLAIM_ID, summary(1L, OTHER.id(), null, "5.00"));
        assertThatThrownBy(() -> claimService.createClaim(request(1L), OWNER))
                .as("someone else's expense").isInstanceOf(InvalidClaimException.class);

        locks(NEW_CLAIM_ID, summary(1L, OWNER.id(), 100L, "5.00"), summary(2L, OWNER.id(), 200L, "5.00"));
        assertThatThrownBy(() -> claimService.createClaim(request(1L, 2L), OWNER))
                .as("mixed trips").isInstanceOf(InvalidClaimException.class);

        locks(NEW_CLAIM_ID, summary(1L, OWNER.id(), null, "5.00"));
        assertThatThrownBy(() -> claimService.createClaim(request(1L, 2L), OWNER))
                .as("fewer expenses locked than requested").isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void locallyKnownConflictsAndDuplicatesFailBeforeAnythingIsSavedOrLocked() {
        assertThatThrownBy(() -> claimService.createClaim(request(1L, 1L), OWNER)).isInstanceOf(InvalidClaimException.class);
        when(claimRepository.findExpenseIdsInActiveClaims(anyCollection(), anyLong())).thenReturn(List.of(1L));
        assertThatThrownBy(() -> claimService.createClaim(request(1L), OWNER)).isInstanceOf(InvalidClaimStateException.class);

        verify(claimRepository, never()).saveAndFlush(any());
        verify(expenseLockClient, never()).lockForClaim(anyLong(), anyLong(), anyCollection());
    }

    @Test
    void onlyAnAdministratorWhoIsNotTheOwnerCanReview() {
        stored(7L, OWNER.id(), "SUBMITTED", 1L);
        stored(8L, ADMIN.id(), "SUBMITTED", 5L);

        assertThatThrownBy(() -> claimService.approveClaim(7L, OWNER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> claimService.approveClaim(7L, OTHER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> claimService.approveClaim(8L, ADMIN)).isInstanceOf(AccessDeniedException.class);

        ClaimResponseDto approved = claimService.approveClaim(7L, ADMIN);
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getReviewedBy()).isEqualTo(ADMIN.id());
        verify(expenseLockClient, never()).releaseClaim(anyLong());
        verifyNoInteractions(claimOutbox);
    }

    @Test
    void rejectingOrDeletingReleasesExpensesForFutureClaims() {
        stored(7L, OWNER.id(), "SUBMITTED", 1L);
        stored(9L, OWNER.id(), "PENDING", 2L);

        assertThat(claimService.rejectClaim(7L, ADMIN).getStatus()).isEqualTo("REJECTED");
        claimService.deleteClaim(9L, OWNER);

        verify(claimOutbox).enqueueRelease(OutboxEventType.CLAIM_REJECTED, 7L, List.of(1L));
        verify(claimOutbox).enqueueRelease(OutboxEventType.CLAIM_DELETED, 9L, Set.of(2L));
        verify(expenseLockClient, never()).releaseClaim(anyLong());
    }

    @Test
    void invalidStatusTransitionsAreRejected() {
        stored(7L, OWNER.id(), "APPROVED", 1L);
        stored(8L, OWNER.id(), "REJECTED", 2L);
        stored(9L, OWNER.id(), "PENDING");

        assertThatThrownBy(() -> claimService.approveClaim(7L, ADMIN)).isInstanceOf(InvalidClaimStateException.class);
        assertThatThrownBy(() -> claimService.rejectClaim(7L, ADMIN)).isInstanceOf(InvalidClaimStateException.class);
        assertThatThrownBy(() -> claimService.approveClaim(8L, ADMIN)).isInstanceOf(InvalidClaimStateException.class);
        assertThatThrownBy(() -> claimService.updateClaim(7L, request(1L), OWNER))
                .isInstanceOf(InvalidClaimStateException.class);
        assertThatThrownBy(() -> claimService.deleteClaim(7L, OWNER)).isInstanceOf(InvalidClaimStateException.class);
        assertThatThrownBy(() -> claimService.approveClaim(9L, ADMIN))
                .as("legacy claim without server-calculated amount").isInstanceOf(InvalidClaimStateException.class);
        verify(claimRepository, never()).delete(any());
        verify(expenseLockClient, never()).releaseClaim(anyLong());
        verifyNoInteractions(claimOutbox);
    }

    @Test
    void otherUsersCannotReadUpdateOrDeleteClaim() {
        stored(7L, OWNER.id(), "SUBMITTED", 1L);

        assertThatThrownBy(() -> claimService.getClaimById(7L, OTHER)).isInstanceOf(ClaimNotFoundException.class);
        assertThatThrownBy(() -> claimService.updateClaim(7L, request(4L), OTHER)).isInstanceOf(ClaimNotFoundException.class);
        assertThatThrownBy(() -> claimService.deleteClaim(7L, OTHER)).isInstanceOf(ClaimNotFoundException.class);
        assertThatThrownBy(() -> claimService.deleteClaim(7L, ADMIN)).isInstanceOf(AccessDeniedException.class);
        assertThat(claimService.getClaimById(7L, ADMIN).getId()).isEqualTo(7L);
    }

    @Test
    void ownerUpdateRelocksAndRecalculates() {
        stored(7L, OWNER.id(), "SUBMITTED", 1L);
        locks(7L, summary(1L, OWNER.id(), null, "40.25"), summary(2L, OWNER.id(), null, "9.75"));

        ClaimResponseDto updated = claimService.updateClaim(7L, request(1L, 2L), OWNER);

        assertThat(updated.getClaimAmount()).isEqualByComparingTo("50.00");
        assertThat(updated.getStatus()).isEqualTo("SUBMITTED");
        verify(claimRepository).findExpenseIdsInActiveClaims(Set.of(1L, 2L), 7L);
    }

    @Test
    void listsAreScopedToOwnerExceptForAdmin() {
        when(claimRepository.findAllByOwnerId(OWNER.id(), Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));
        when(claimRepository.findAll(Pageable.unpaged())).thenReturn(new PageImpl<>(List.of()));

        claimService.getAllClaims(OWNER, Pageable.unpaged());
        claimService.getAllClaims(ADMIN, Pageable.unpaged());

        verify(claimRepository).findAllByOwnerId(OWNER.id(), Pageable.unpaged());
        verify(claimRepository).findAll(Pageable.unpaged());
    }

    private void locks(long claimId, ExpenseSummary... summaries) {
        when(expenseLockClient.lockForClaim(eq(claimId), anyLong(), anyCollection())).thenReturn(List.of(summaries));
    }

    private static ExpenseSummary summary(Long id, Long ownerId, Long tripId, String amount) {
        return new ExpenseSummary(id, ownerId, tripId, new BigDecimal(amount));
    }

    private void stored(Long id, Long ownerId, String status, Long... expenseIds) {
        when(claimRepository.findById(id)).thenReturn(Optional.of(Claim.builder().id(id).claimNumber("C-" + id).title("t")
                .claimAmount(BigDecimal.ONE).status(status).ownerId(ownerId)
                .expenseIds(new LinkedHashSet<>(List.of(expenseIds))).build()));
    }

    private static ClaimRequestDto request(Long... expenseIds) {
        return ClaimRequestDto.builder().claimNumber("C-NEW").title("Trip claim").expenseIds(List.of(expenseIds)).build();
    }
}
