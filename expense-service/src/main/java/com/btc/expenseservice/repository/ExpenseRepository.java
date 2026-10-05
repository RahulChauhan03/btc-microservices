package com.btc.expenseservice.repository;

import com.btc.expenseservice.dto.CategoryTotalDto;
import com.btc.expenseservice.dto.MonthTotalDto;
import com.btc.expenseservice.dto.TripSpendDto;
import com.btc.expenseservice.entity.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /** Optional owner and trip; the date bounds are always set (DateRange, or the widest dates for a trip). */
    String SCOPE = " where (:ownerId is null or e.ownerId = :ownerId) and (:tripId is null or e.tripId = :tripId)"
            + " and e.expenseDate between :from and :to";

    @Query("select new com.btc.expenseservice.dto.CategoryTotalDto(e.category, count(e), sum(e.amount))"
            + " from Expense e" + SCOPE + " group by e.category order by sum(e.amount) desc")
    List<CategoryTotalDto> totalsByCategory(@Param("ownerId") Long ownerId, @Param("tripId") Long tripId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("select new com.btc.expenseservice.dto.MonthTotalDto(year(e.expenseDate), month(e.expenseDate),"
            + " count(e), sum(e.amount)) from Expense e" + SCOPE
            + " group by year(e.expenseDate), month(e.expenseDate)"
            + " order by year(e.expenseDate), month(e.expenseDate)")
    List<MonthTotalDto> totalsByMonth(@Param("ownerId") Long ownerId, @Param("tripId") Long tripId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    @Query(value = "select new com.btc.expenseservice.dto.TripSpendDto(e.tripId, count(e), sum(e.amount)) from Expense e"
            + " where e.tripId is not null and e.expenseDate between :from and :to group by e.tripId"
            + " order by sum(e.amount) desc, e.tripId",
            countQuery = "select count(distinct e.tripId) from Expense e where e.tripId is not null"
            + " and e.expenseDate between :from and :to")
    Page<TripSpendDto> totalsByTrip(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    long countByExpenseDateBetween(LocalDate from, LocalDate to);

    List<Expense> findAllByExpenseDateBetweenOrderByExpenseDateAscIdAsc(LocalDate from, LocalDate to);

    /**
     * The trip's expenses, write-locked: in InnoDB this also blocks concurrent inserts for the same trip, so two
     * simultaneous expenses cannot both pass the policy's trip limit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.tripId = :tripId")
    List<Expense> lockTripExpenses(@Param("tripId") Long tripId);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.tripId = :tripId")
    BigDecimal totalForTrip(@Param("tripId") Long tripId);

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
