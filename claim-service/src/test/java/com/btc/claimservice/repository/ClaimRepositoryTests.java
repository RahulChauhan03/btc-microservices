package com.btc.claimservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.btc.claimservice.entity.Claim;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Pageable;

/** Runs the JPQL against an in-memory database, so a broken query fails here rather than at startup. */
@DataJpaTest
class ClaimRepositoryTests {

    @Autowired
    private ClaimRepository claimRepository;

    @Test
    void findsExpensesAlreadyInClaimsThatAreNotRejected() {
        Claim submitted = save("C-1", "SUBMITTED", 1L, 2L);
        save("C-2", "REJECTED", 3L);
        save("C-3", "APPROVED", 4L);

        assertThat(claimRepository.findExpenseIdsInActiveClaims(List.of(1L, 3L, 4L, 5L), -1L))
                .containsExactlyInAnyOrder(1L, 4L);
        assertThat(claimRepository.findExpenseIdsInActiveClaims(List.of(1L, 2L, 4L), submitted.getId()))
                .containsExactly(4L);
    }

    @Test
    void findsClaimsByOwner() {
        Claim mine = save("C-4", "SUBMITTED", 6L);
        mine.setOwnerId(10L);
        claimRepository.save(mine);

        assertThat(claimRepository.findAllByOwnerId(10L, Pageable.unpaged()).getContent()).extracting(Claim::getClaimNumber).containsExactly("C-4");
    }

    private Claim save(String number, String status, Long... expenseIds) {
        return claimRepository.save(Claim.builder().claimNumber(number).title("t").claimAmount(BigDecimal.ONE)
                .status(status).expenseIds(new LinkedHashSet<>(List.of(expenseIds))).build());
    }
}
