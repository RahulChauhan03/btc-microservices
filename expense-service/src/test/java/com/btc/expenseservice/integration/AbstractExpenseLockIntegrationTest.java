package com.btc.expenseservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.expenseservice.client.TripClient;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.exception.ExpenseConflictException;
import com.btc.expenseservice.exception.InvalidExpenseException;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.security.TestJwt;
import com.btc.expenseservice.service.impl.ExpenseLockService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Expense locks end to end against a real database: HTTP + security + service + repository. Subclasses choose
 * the database (H2 for every build, MySQL when configured).
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractExpenseLockIntegrationTest {

    private static final long OWNER = 10L;
    private static final String EXPENSE_JSON =
            "{\"title\":\"Lunch\",\"amount\":12.00,\"category\":\"MEAL\",\"expenseDate\":\"2026-09-01\"}";

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseLockService expenseLockService;

    @MockitoBean
    private TripClient tripClient;

    @AfterEach
    void cleanUp() {
        expenseRepository.deleteAll();
    }

    @Test
    void internalLockApiAcceptsOnlyServiceTokens() throws Exception {
        long id = expense(OWNER, null);
        String body = "{\"ownerId\":" + OWNER + ",\"expenseIds\":[" + id + "]}";

        mockMvc.perform(put("/expenses/internal/claims/7/locks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"EMPLOYEE", "ADMIN"}) {
            mockMvc.perform(put("/expenses/internal/claims/7/locks").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OWNER, role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        assertThat(lockOf(id)).isNull();

        mockMvc.perform(put("/expenses/internal/claims/7/locks").header(HttpHeaders.AUTHORIZATION, TestJwt.service())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimId").value(7));
        // A service token is not a user: it cannot be used on user-facing endpoints.
        mockMvc.perform(get("/expenses").header(HttpHeaders.AUTHORIZATION, TestJwt.service()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockedExpenseCannotBeChangedUntilTheClaimReleasesIt() throws Exception {
        long id = expense(OWNER, null);
        expenseLockService.lockForClaim(7L, OWNER, List.of(id));
        String owner = TestJwt.bearer(OWNER, "EMPLOYEE");

        mockMvc.perform(put("/expenses/" + id).header(HttpHeaders.AUTHORIZATION, owner)
                        .contentType(MediaType.APPLICATION_JSON).content(EXPENSE_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Expense is included in claim 7 and cannot be changed"));
        mockMvc.perform(delete("/expenses/" + id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/expenses/internal/claims/7/locks").header(HttpHeaders.AUTHORIZATION, TestJwt.service()))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/expenses/" + id).header(HttpHeaders.AUTHORIZATION, owner)
                        .contentType(MediaType.APPLICATION_JSON).content(EXPENSE_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/expenses/" + id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
    }

    @Test
    void lockIsAllOrNothing() {
        long mine = expense(OWNER, null);
        long foreign = expense(20L, null);
        long onTrip1 = expense(OWNER, 100L);
        long onTrip2 = expense(OWNER, 200L);

        assertThatThrownBy(() -> expenseLockService.lockForClaim(7L, OWNER, List.of(mine, foreign)))
                .isInstanceOf(InvalidExpenseException.class);
        assertThatThrownBy(() -> expenseLockService.lockForClaim(7L, OWNER, List.of(onTrip1, onTrip2)))
                .isInstanceOf(InvalidExpenseException.class);
        assertThatThrownBy(() -> expenseLockService.lockForClaim(7L, OWNER, List.of(mine, 999_999L)))
                .isInstanceOf(InvalidExpenseException.class);

        assertThat(List.of(lockOrZero(mine), lockOrZero(foreign), lockOrZero(onTrip1), lockOrZero(onTrip2)))
                .containsOnly(0L);
    }

    @Test
    void anotherClaimIsRefusedWhileRelockingByTheSameClaimIsIdempotent() {
        long a = expense(OWNER, null);
        long b = expense(OWNER, null);

        expenseLockService.lockForClaim(7L, OWNER, List.of(a, b));
        assertThatThrownBy(() -> expenseLockService.lockForClaim(8L, OWNER, List.of(b)))
                .isInstanceOf(ExpenseConflictException.class);
        expenseLockService.lockForClaim(7L, OWNER, List.of(a, b));

        // Updating the claim to cover only b releases a for future claims.
        expenseLockService.lockForClaim(7L, OWNER, List.of(b));
        assertThat(lockOf(a)).isNull();
        assertThat(lockOf(b)).isEqualTo(7L);
        expenseLockService.lockForClaim(8L, OWNER, List.of(a));
        assertThat(lockOf(a)).isEqualTo(8L);
    }

    @Test
    void repeatedReleaseIsIdempotentAndNeverTouchesAnotherClaimsLocks() {
        long a = expense(OWNER, null);
        long b = expense(OWNER, null);
        long c = expense(OWNER, null);
        expenseLockService.lockForClaim(7L, OWNER, List.of(a));
        expenseLockService.lockForClaim(8L, OWNER, List.of(b, c));

        // claim-service's outbox delivers at least once, so the same release may arrive repeatedly.
        assertThat(expenseLockService.releaseClaim(7L)).isEqualTo(1);
        assertThat(expenseLockService.releaseClaim(7L)).isZero();
        assertThat(expenseLockService.releaseClaim(9L)).as("unknown or already-released claim").isZero();

        assertThat(lockOf(a)).isNull();
        assertThat(lockOf(b)).isEqualTo(8L);
        assertThat(lockOf(c)).isEqualTo(8L);
    }

    @Test
    void concurrentClaimsForTheSameExpenseCannotBothSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                long id = expense(OWNER, null);
                long claimA = 1000L + round * 2;
                long claimB = claimA + 1;
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Long>> attempts = new ArrayList<>();
                for (long claimId : new long[] {claimA, claimB}) {
                    Callable<Long> attempt = () -> {
                        start.await();
                        expenseLockService.lockForClaim(claimId, OWNER, List.of(id));
                        return claimId;
                    };
                    attempts.add(pool.submit(attempt));
                }
                start.countDown();

                List<Long> winners = new ArrayList<>();
                for (Future<Long> attempt : attempts) {
                    try {
                        winners.add(attempt.get(30, TimeUnit.SECONDS));
                    } catch (java.util.concurrent.ExecutionException failure) {
                        assertThat(failure.getCause())
                                .isInstanceOfAny(ExpenseConflictException.class, ConcurrencyFailureException.class);
                    }
                }
                assertThat(winners).as("round %d", round).hasSize(1);
                assertThat(lockOf(id)).isEqualTo(winners.get(0));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleWriteCannotOverwriteAConcurrentLock() {
        long id = expense(OWNER, null);
        Expense stale = expenseRepository.findById(id).orElseThrow();

        expenseLockService.lockForClaim(7L, OWNER, List.of(id));
        stale.setAmount(new BigDecimal("999.00"));

        assertThatThrownBy(() -> expenseRepository.save(stale)).isInstanceOf(ConcurrencyFailureException.class);
        Expense current = expenseRepository.findById(id).orElseThrow();
        assertThat(current.getAmount()).isEqualByComparingTo("5.00");
        assertThat(current.getClaimId()).isEqualTo(7L);
    }

    private long expense(long ownerId, Long tripId) {
        return expenseRepository.save(Expense.builder().title("Cab").amount(new BigDecimal("5.00")).category("TRAVEL")
                .expenseDate(LocalDate.of(2026, 9, 1)).ownerId(ownerId).tripId(tripId).build()).getId();
    }

    private Long lockOf(long id) {
        return expenseRepository.findById(id).orElseThrow().getClaimId();
    }

    private long lockOrZero(long id) {
        Long lock = lockOf(id);
        return lock == null ? 0L : lock;
    }
}
