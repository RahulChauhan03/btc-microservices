package com.btc.claimservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.exception.DependencyUnavailableException;
import com.btc.claimservice.outbox.OutboxEvent;
import com.btc.claimservice.outbox.OutboxEventRepository;
import com.btc.claimservice.outbox.OutboxStatus;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.CurrentUser;
import com.btc.claimservice.security.TestJwt;
import com.btc.claimservice.service.ClaimService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The real asynchronous paths (after-commit dispatch and the scheduled poll) with the system clock. */
class ClaimOutboxBackgroundDeliveryTests {

    private static final CurrentUser ADMIN = new CurrentUser(1L, true);
    private static final CurrentUser OWNER = new CurrentUser(10L, false);

    abstract static class Base {

        @DynamicPropertySource
        static void jwtProperties(DynamicPropertyRegistry registry) {
            TestJwt.register(registry);
        }

        @Autowired
        ClaimService claimService;

        @Autowired
        ClaimRepository claimRepository;

        @Autowired
        OutboxEventRepository outboxEventRepository;

        @MockitoBean
        ExpenseLockClient expenseLockClient;

        @AfterEach
        void cleanUp() {
            outboxEventRepository.deleteAll();
            claimRepository.deleteAll();
        }

        long storedClaim(String number) {
            return claimRepository.save(Claim.builder().claimNumber(number).title("t").claimAmount(BigDecimal.TEN)
                    .status("SUBMITTED").ownerId(OWNER.id()).expenseIds(new LinkedHashSet<>(List.of(1L))).build()).getId();
        }

        OutboxEvent onlyEventOf(long claimId) {
            return outboxEventRepository.findAllByClaimIdOrderById(claimId).get(0);
        }
    }

    /** Poll interval of an hour: only the after-commit dispatch can deliver within the test. */
    @Nested
    @SpringBootTest(properties = {"btc.outbox.dispatch-after-commit=true", "btc.outbox.scheduling-enabled=true",
            "btc.outbox.poll-interval=PT1H"})
    class AfterCommitDispatch extends Base {

        @Test
        void deletionIsReleasedRightAfterItCommits() {
            long id = storedClaim("BG-D");

            claimService.deleteClaim(id, OWNER);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(onlyEventOf(id).getStatus()).isEqualTo(OutboxStatus.COMPLETED));
            verify(expenseLockClient).releaseClaim(id);
        }
    }

    /** No immediate dispatch: the poll picks the event up and retries it until expense-service is back. */
    @Nested
    @SpringBootTest(properties = {"btc.outbox.dispatch-after-commit=false", "btc.outbox.scheduling-enabled=true",
            "btc.outbox.poll-interval=PT0.2S", "btc.outbox.initial-backoff=PT0.1S", "btc.outbox.max-backoff=PT0.4S"})
    class ScheduledPoll extends Base {

        @Test
        void rejectionIsReleasedOnceExpenseServiceRecovers() {
            long id = storedClaim("BG-R");
            doThrow(new DependencyUnavailableException("Expense service is unavailable", null))
                    .when(expenseLockClient).releaseClaim(id);

            claimService.rejectClaim(id, ADMIN);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(onlyEventOf(id).getAttempts()).isGreaterThanOrEqualTo(2));
            assertThat(onlyEventOf(id).getStatus()).isNotEqualTo(OutboxStatus.COMPLETED);

            doNothing().when(expenseLockClient).releaseClaim(id);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(onlyEventOf(id).getStatus()).isEqualTo(OutboxStatus.COMPLETED));
        }
    }
}
