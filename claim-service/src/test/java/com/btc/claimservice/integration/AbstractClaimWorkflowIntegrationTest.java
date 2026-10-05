package com.btc.claimservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.claimservice.audit.AuditLogRepository;
import com.btc.claimservice.client.ExpenseLockClient.ExpenseSummary;
import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.exception.DependencyUnavailableException;
import com.btc.claimservice.exception.InvalidClaimStateException;
import com.btc.claimservice.outbox.OutboxEvent;
import com.btc.claimservice.outbox.OutboxEventRepository;
import com.btc.claimservice.outbox.OutboxEventType;
import com.btc.claimservice.outbox.OutboxStatus;
import com.btc.claimservice.reimbursement.ReimbursementRepository;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.CurrentUser;
import com.btc.claimservice.security.TestJwt;
import com.btc.claimservice.service.ClaimService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Claim workflow with real transactions and database; only expense-service is replaced by a mock. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractClaimWorkflowIntegrationTest {

    private static final long OWNER = 10L;
    private static final CurrentUser ADMIN = new CurrentUser(1L, true);
    private static final String CLAIM_JSON = "{\"claimNumber\":\"C-%s\",\"title\":\"Trip\",\"expenseIds\":[1,2]}";

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimService claimService;

    private TransactionTemplate transaction;

    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private ExpenseLockClient expenseLockClient;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void cleanUp() {
        reimbursementRepository.deleteAll();
        auditLogRepository.deleteAll();
        claimRepository.deleteAll();
        outboxEventRepository.deleteAll();
        reset(expenseLockClient);
    }

    @Test
    void submittedClaimTakesItsAmountFromTheLockedExpenses() throws Exception {
        when(expenseLockClient.lockForClaim(anyLong(), eq(OWNER), anyCollection())).thenReturn(List.of(
                new ExpenseSummary(1L, OWNER, 100L, new BigDecimal("40.25")),
                new ExpenseSummary(2L, OWNER, 100L, new BigDecimal("9.75"))));

        mockMvc.perform(post("/claims").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OWNER, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_JSON.formatted("1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimAmount").value(50.00))
                .andExpect(jsonPath("$.tripId").value(100))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/claims?size=10&sort=claimAmount,desc").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OWNER, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].expenseIds.length()").value(2));
    }

    @Test
    void lockConflictRollsBackTheClaimAndUndoesAnyLock() throws Exception {
        when(expenseLockClient.lockForClaim(anyLong(), eq(OWNER), anyCollection()))
                .thenThrow(new InvalidClaimStateException("Expenses already included in another claim: [2]"));

        mockMvc.perform(post("/claims").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OWNER, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_JSON.formatted("2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Expenses already included in another claim: [2]"));

        assertThat(claimRepository.count()).as("claim row rolled back").isZero();
        assertRollbackReleaseRecorded();
    }

    @Test
    void failureAfterLockingRollsBackAndReleases() throws Exception {
        // expense-service answered with expenses from two trips: the claim must not be kept.
        when(expenseLockClient.lockForClaim(anyLong(), eq(OWNER), anyCollection())).thenReturn(List.of(
                new ExpenseSummary(1L, OWNER, 100L, BigDecimal.ONE),
                new ExpenseSummary(2L, OWNER, 200L, BigDecimal.ONE)));

        mockMvc.perform(post("/claims").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OWNER, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_JSON.formatted("3")))
                .andExpect(status().isBadRequest());

        assertThat(claimRepository.count()).isZero();
        assertRollbackReleaseRecorded();
    }

    @Test
    void expenseServiceOutageIsA503AndLeavesNoClaim() throws Exception {
        when(expenseLockClient.lockForClaim(anyLong(), eq(OWNER), anyCollection()))
                .thenThrow(new DependencyUnavailableException("Expense service is unavailable", null));

        mockMvc.perform(post("/claims").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OWNER, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_JSON.formatted("4")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
        assertThat(claimRepository.count()).isZero();
    }

    @Test
    void rejectionAndDeletionReleaseExpensesButApprovalKeepsThemLocked() throws Exception {
        long rejected = storedClaim("R");
        long approved = storedClaim("A");
        long deleted = storedClaim("D");
        String admin = TestJwt.bearer(1, "ADMIN");

        mockMvc.perform(post("/claims/" + rejected + "/reject").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/claims/" + approved + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/claims/" + deleted).header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(OWNER, "EMPLOYEE")))
                .andExpect(status().isNoContent());

        assertThat(outboxEventRepository.findAllByClaimIdOrderById(rejected)).extracting(OutboxEvent::getEventType)
                .containsExactly(OutboxEventType.CLAIM_REJECTED, OutboxEventType.NOTIFICATION);
        assertThat(outboxEventRepository.findAllByClaimIdOrderById(deleted)).extracting(OutboxEvent::getEventType)
                .containsExactly(OutboxEventType.CLAIM_DELETED);
        assertThat(outboxEventRepository.findAllByClaimIdOrderById(approved)).extracting(OutboxEvent::getEventType)
                .as("approval keeps the locks; only the owner is notified").containsExactly(OutboxEventType.NOTIFICATION);
        assertThat(outboxEventRepository.findAllByClaimIdOrderById(approved).get(0).getPayload())
                .contains("\"recipientId\":" + OWNER).contains("CLAIM_APPROVED").doesNotContain("token");
        // Delivery is asynchronous (driven directly in the outbox suite); nothing is sent inside the request.
        verify(expenseLockClient, never()).releaseClaim(anyLong());
    }

    @Test
    void rejectionCommitsWithItsOutboxEventEvenWhileExpenseServiceIsDown() throws Exception {
        long id = storedClaim("F", 4L, 5L);
        doThrow(new DependencyUnavailableException("down", null)).when(expenseLockClient).releaseClaim(id);

        mockMvc.perform(post("/claims/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(claimRepository.findById(id).orElseThrow().getStatus()).isEqualTo("REJECTED");
        OutboxEvent event = outboxEventRepository.findAllByClaimIdOrderById(id).get(0);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.expenseIdList()).containsExactly(4L, 5L);
    }

    @Test
    void rolledBackRejectionOrDeletionLeavesNoOutboxEvent() {
        long rejected = storedClaim("RB1");
        long deleted = storedClaim("RB2");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            claimService.rejectClaim(rejected, ADMIN);
            claimService.deleteClaim(deleted, new CurrentUser(OWNER, false));
            throw new IllegalStateException("simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(claimRepository.findById(rejected).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
        assertThat(claimRepository.findById(deleted)).isPresent();
        assertThat(outboxEventRepository.count()).isZero();
    }

    private void assertRollbackReleaseRecorded() {
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(OutboxEventType.CLAIM_CREATION_ROLLED_BACK);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.expenseIdList()).containsExactly(1L, 2L);
            assertThat(event.getNextAttemptAt()).as("delayed past the lock call's timeout").isAfter(event.getCreatedAt());
        });
        verify(expenseLockClient, never()).releaseClaim(anyLong());
    }

    @Test
    void concurrentReviewsCannotBothSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                long id = storedClaim("CR" + round);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<String>> reviews = new ArrayList<>();
                reviews.add(pool.submit(() -> {
                    start.await();
                    return claimService.approveClaim(id, ADMIN).getStatus();
                }));
                reviews.add(pool.submit(() -> {
                    start.await();
                    return claimService.rejectClaim(id, ADMIN).getStatus();
                }));
                start.countDown();

                List<String> outcomes = new ArrayList<>();
                for (Future<String> review : reviews) {
                    try {
                        outcomes.add(review.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException failure) {
                        assertThat(failure.getCause())
                                .isInstanceOfAny(InvalidClaimStateException.class, ConcurrencyFailureException.class);
                    }
                }
                assertThat(outcomes).as("round %d", round).hasSize(1);
                assertThat(claimRepository.findById(id).orElseThrow().getStatus()).isEqualTo(outcomes.get(0));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleWriteAfterAReviewIsRejected() {
        long id = storedClaim("S");
        Claim stale = claimRepository.findById(id).orElseThrow();

        claimService.approveClaim(id, ADMIN);
        stale.setTitle("edited concurrently");

        assertThatThrownBy(() -> claimRepository.save(stale)).isInstanceOf(ConcurrencyFailureException.class);
        assertThat(claimRepository.findById(id).orElseThrow().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void activeClaimQueryIgnoresRejectedClaimsAndTheClaimBeingEdited() {
        long submitted = storedClaim("Q1", 1L, 2L);
        Claim rejected = claimRepository.findById(storedClaim("Q2", 3L)).orElseThrow();
        rejected.setStatus("REJECTED");
        claimRepository.save(rejected);

        assertThat(claimRepository.findExpenseIdsInActiveClaims(List.of(1L, 3L, 5L), -1L)).containsExactly(1L);
        assertThat(claimRepository.findExpenseIdsInActiveClaims(List.of(1L, 2L), submitted)).isEmpty();
    }

    private long storedClaim(String suffix, Long... expenseIds) {
        Long[] ids = expenseIds.length == 0 ? new Long[] {1L} : expenseIds;
        return claimRepository.save(Claim.builder().claimNumber("C-" + suffix).title("t").claimAmount(BigDecimal.TEN)
                .status("SUBMITTED").ownerId(OWNER).expenseIds(new LinkedHashSet<>(List.of(ids))).build()).getId();
    }
}
