package com.btc.expenseservice.repository;

import com.btc.expenseservice.entity.Expense;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<Expense> findAllByOwnerIdAndTripId(Long ownerId, Long tripId, Pageable pageable);

    Page<Expense> findAllByTripId(Long tripId, Pageable pageable);

    /**
     * Locks the owner's expenses for a claim in one statement. A row already locked by a different claim
     * does not match, so of two concurrent claims for the same expense only one can update it (InnoDB
     * re-checks the condition on the latest committed row). Returns the number of rows locked.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Expense e set e.claimId = :claimId, e.version = e.version + 1 "
            + "where e.id in :ids and e.ownerId = :ownerId and (e.claimId is null or e.claimId = :claimId)")
    int lockForClaim(@Param("ids") Collection<Long> ids, @Param("ownerId") Long ownerId,
                     @Param("claimId") Long claimId);

    /** Releases expenses the claim no longer covers (used when a pending claim's expense list changes). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Expense e set e.claimId = null, e.version = e.version + 1 "
            + "where e.claimId = :claimId and e.id not in :keepIds")
    int releaseFromClaimExcept(@Param("claimId") Long claimId, @Param("keepIds") Collection<Long> keepIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Expense e set e.claimId = null, e.version = e.version + 1 where e.claimId = :claimId")
    int releaseFromClaim(@Param("claimId") Long claimId);
}
