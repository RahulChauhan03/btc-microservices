package com.btc.claimservice.repository;

import com.btc.claimservice.dto.ClaimStatusTotalDto;
import com.btc.claimservice.entity.Claim;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    @Query("select new com.btc.claimservice.dto.ClaimStatusTotalDto(c.status, count(c), sum(c.claimAmount))"
            + " from Claim c where (:ownerId is null or c.ownerId = :ownerId) group by c.status order by c.status")
    List<ClaimStatusTotalDto> totalsByStatus(@Param("ownerId") Long ownerId);

    /** Claims submitted in [from, toExclusive), by status. */
    @Query("select new com.btc.claimservice.dto.ClaimStatusTotalDto(c.status, count(c), sum(c.claimAmount))"
            + " from Claim c where c.submittedAt >= :from and c.submittedAt < :toExclusive group by c.status order by c.status")
    List<ClaimStatusTotalDto> totalsByStatusSubmittedBetween(@Param("from") LocalDateTime from,
                                                              @Param("toExclusive") LocalDateTime toExclusive);

    boolean existsByClaimNumber(String claimNumber);

    boolean existsByClaimNumberAndIdNot(String claimNumber, Long id);

    Page<Claim> findAllByOwnerId(Long ownerId, Pageable pageable);

    @Query("select c.status from Claim c where c.id = :id")
    Optional<String> findStatusById(@Param("id") Long id);

    /**
     * Expense ids from {@code expenseIds} already covered by another claim that has not been rejected.
     * Pass {@code excludeClaimId = -1} when creating a claim.
     */
    @Query("select distinct e from Claim c join c.expenseIds e "
            + "where e in :expenseIds and c.status <> 'REJECTED' and c.id <> :excludeClaimId")
    List<Long> findExpenseIdsInActiveClaims(@Param("expenseIds") Collection<Long> expenseIds,
                                            @Param("excludeClaimId") Long excludeClaimId);
}
