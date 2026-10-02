package com.btc.claimservice.repository;

import com.btc.claimservice.entity.Claim;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    boolean existsByClaimNumber(String claimNumber);

    boolean existsByClaimNumberAndIdNot(String claimNumber, Long id);

    Page<Claim> findAllByOwnerId(Long ownerId, Pageable pageable);

    /**
     * Expense ids from {@code expenseIds} already covered by another claim that has not been rejected.
     * Pass {@code excludeClaimId = -1} when creating a claim.
     */
    @Query("select distinct e from Claim c join c.expenseIds e "
            + "where e in :expenseIds and c.status <> 'REJECTED' and c.id <> :excludeClaimId")
    List<Long> findExpenseIdsInActiveClaims(@Param("expenseIds") Collection<Long> expenseIds,
                                            @Param("excludeClaimId") Long excludeClaimId);
}
