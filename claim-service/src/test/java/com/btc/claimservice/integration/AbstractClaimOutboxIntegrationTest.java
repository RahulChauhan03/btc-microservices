package com.btc.claimservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.client.NotificationClient;
import com.btc.claimservice.client.NotificationMessage;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.exception.DependencyUnavailableException;
import com.btc.claimservice.exception.ExpenseServiceRejectedException;
import com.btc.claimservice.exception.PermanentDeliveryException;
import com.btc.claimservice.outbox.ClaimOutbox;
import com.btc.claimservice.outbox.OutboxConfig;
import com.btc.claimservice.outbox.OutboxEvent;
import com.btc.claimservice.outbox.OutboxEventRepository;
import com.btc.claimservice.outbox.OutboxEventType;
import com.btc.claimservice.outbox.OutboxMetrics;
import com.btc.claimservice.outbox.OutboxProcessor;
import com.btc.claimservice.outbox.OutboxProperties;
import com.btc.claimservice.outbox.OutboxStatus;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.TestJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox delivery with real transactions and database; expense-service is a mock and time is a controllable
 * clock. "Restarts" are simulated with new processor instances (new worker ids) over the same database.
 */
@SpringBootTest
@Import(AbstractClaimOutboxIntegrationTest.ClockConfig.class)
abstract class AbstractClaimOutboxIntegrationTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    /** Same statement as docs/operations/phase-5-claim-outbox.md. */
    static final String MANUAL_RETRY_SQL = "UPDATE claim_outbox SET status = 'PENDING', attempts = 0, "
            + "next_attempt_at = '2000-01-01 00:00:00', last_error = NULL, locked_by = NULL, locked_until = NULL "
            + "WHERE event_id = ? AND status = 'FAILED'";

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    private MutableClock clock;

    @Autowired
    private ClaimOutbox claimOutbox;

    @Autowired
    private OutboxProcessor processor;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private OutboxMetrics metrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ExpenseLockClient expenseLockClient;

    @MockitoBean
    private NotificationClient notificationClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        claimRepository.deleteAll();
        reset(expenseLockClient);
    }

    @Test
    void successfulReleaseCompletesTheEvent() {
        Long id = enqueue(9001L);

        assertThat(processor.processDue()).isEqualTo(1);

        verify(expenseLockClient).releaseClaim(9001L);
        OutboxEvent event = event(id);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getCompletedAt()).isNotNull();
        assertThat(event.getLockedBy()).isNull();
        assertThat(processor.processDue()).as("completed events are never delivered again").isZero();
    }

    @Test
    void expenseServiceDowntimeIsRetriedWithBackoffUntilItRecovers() {
        Long id = enqueue(9002L);
        doThrow(new DependencyUnavailableException("Expense service is unavailable", null))
                .when(expenseLockClient).releaseClaim(9002L);

        processor.processDue();
        OutboxEvent afterFirst = event(id);
        assertThat(afterFirst.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getLastError()).contains("Expense service is unavailable");
        assertThat(afterFirst.getNextAttemptAt()).isEqualTo(now().plusSeconds(5));

        clock.advance(Duration.ofSeconds(4));
        assertThat(processor.processDue()).as("not due before the backoff").isZero();
        clock.advance(Duration.ofSeconds(1));
        processor.processDue();
        assertThat(event(id).getNextAttemptAt()).as("backoff doubles").isEqualTo(now().plusSeconds(10));

        doNothing().when(expenseLockClient).releaseClaim(9002L);
        clock.advance(Duration.ofSeconds(10));
        processor.processDue();

        OutboxEvent recovered = event(id);
        assertThat(recovered.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(recovered.getAttempts()).isEqualTo(3);
        assertThat(recovered.getLastError()).isNull();
        verify(expenseLockClient, times(3)).releaseClaim(9002L);
    }

    @Test
    void restartedServiceDeliversPendingEventsAndTakesOverAbandonedOnes() {
        Long pending = enqueue(9003L);
        Long abandoned = enqueue(9004L);
        // A worker took this event and then the service died before finishing it.
        assertThat((int) new TransactionTemplate(transactionManager).execute(status -> outboxEventRepository.acquire(
                abandoned, "dead-worker", now(), now().plus(properties.lease())))).isEqualTo(1);

        OutboxProcessor restarted = newProcessor();
        assertThat(restarted.processDue()).as("abandoned event is still leased").isEqualTo(1);
        assertThat(event(pending).getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(event(abandoned).getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);

        clock.advance(properties.lease().plusSeconds(1));
        assertThat(restarted.processDue()).isEqualTo(1);
        assertThat(event(abandoned).getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(event(abandoned).getAttempts()).isEqualTo(2);

        // The dead worker coming back late cannot overwrite the outcome.
        Integer late = new TransactionTemplate(transactionManager).execute(status -> outboxEventRepository.fail(
                abandoned, "dead-worker", now(), "late"));
        assertThat(late).isZero();
        assertThat(event(abandoned).getStatus()).isEqualTo(OutboxStatus.COMPLETED);
    }

    @Test
    void duplicateDeliveryIsHarmlessAndCompletedEventsAreNotTakenAgain() {
        Long id = enqueue(9005L);
        processor.processDue();

        assertThat(processor.process(id)).isFalse();
        assertThat(newProcessor().process(id)).isFalse();
        verify(expenseLockClient, times(1)).releaseClaim(9005L);

        // Re-queuing a completed event (operator mistake) with the runbook's manual-retry SQL just repeats the
        // idempotent, claim-scoped release.
        assertThat(jdbcTemplate.update(MANUAL_RETRY_SQL.replace("status = 'FAILED'", "status = 'COMPLETED'"),
                event(id).getEventId())).isEqualTo(1);
        processor.processDue();
        assertThat(event(id).getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        verify(expenseLockClient, times(2)).releaseClaim(9005L);
    }

    @Test
    void releaseIsNeverSentForAClaimThatIsStillActive() {
        Long claimId = claimRepository.save(Claim.builder().claimNumber("C-ACTIVE").title("t")
                .claimAmount(BigDecimal.TEN).status("SUBMITTED").ownerId(10L)
                .expenseIds(new LinkedHashSet<>(List.of(1L))).build()).getId();
        Long id = enqueue(claimId);

        processor.processDue();

        verify(expenseLockClient, never()).releaseClaim(anyLong());
        assertThat(event(id).getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event(id).getLastError()).contains("must stay locked");
    }

    @Test
    void retriesStopAtTheLimitAndTheEventStaysFailedForRecovery() {
        Long id = enqueue(9006L);
        doThrow(new DependencyUnavailableException("Expense service is unavailable", null))
                .when(expenseLockClient).releaseClaim(9006L);

        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            assertThat(processor.processDue()).as("attempt %d", attempt).isEqualTo(1);
            clock.advance(properties.maxBackoff());
        }

        OutboxEvent failed = event(id);
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(properties.maxAttempts());
        assertThat(failed.getLastError()).contains("Expense service is unavailable");
        assertThat(failed.expenseIdList()).containsExactly(1L, 2L);
        clock.advance(Duration.ofDays(1));
        assertThat(processor.processDue()).isZero();
        verify(expenseLockClient, times(properties.maxAttempts())).releaseClaim(9006L);

        metrics.refresh();
        assertThat(meterRegistry.get("btc.claim.outbox.events").tag("state", "failed").gauge().value()).isEqualTo(1.0);

        // Recovery: after the cause is fixed, the documented SQL re-queues the event with a fresh attempt budget.
        doNothing().when(expenseLockClient).releaseClaim(9006L);
        assertThat(jdbcTemplate.update(MANUAL_RETRY_SQL, failed.getEventId())).isEqualTo(1);
        processor.processDue();
        assertThat(event(id).getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(event(id).getAttempts()).isEqualTo(1);
    }

    @Test
    void notificationsAreDeliveredWithTheirOutboxEventIdAndNeverTriggerAReleaseOrItsGuard() {
        Long claimId = claimRepository.save(Claim.builder().claimNumber("C-NOTIFY").title("t")
                .claimAmount(BigDecimal.TEN).status("APPROVED").ownerId(10L)
                .expenseIds(new LinkedHashSet<>(List.of(1L))).build()).getId();
        String eventId = new TransactionTemplate(transactionManager).execute(status -> claimOutbox.enqueueNotification(
                claimId, NotificationMessage.toUser(10L, 1L, "CLAIM_APPROVED", "Claim approved", "Approved.", "/claims")));
        doThrow(new DependencyUnavailableException("Notification service is unavailable", null))
                .doNothing().when(notificationClient).publish(eq(eventId), any());

        processor.processDue();
        assertThat(outboxEventRepository.findByEventId(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
        clock.advance(properties.initialBackoff());
        processor.processDue();

        assertThat(outboxEventRepository.findByEventId(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        verify(notificationClient, times(2)).publish(eq(eventId), argThat(message -> "USER".equals(message.audience())
                && Long.valueOf(10L).equals(message.recipientId()) && "Claim approved".equals(message.title())));
        verify(expenseLockClient, never()).releaseClaim(anyLong());
    }

    @Test
    void refusedNotificationFailsWithoutAffectingExpenseLocks() {
        String eventId = new TransactionTemplate(transactionManager).execute(status -> claimOutbox.enqueueNotification(
                9300L, NotificationMessage.toAdmins(10L, "CLAIM_SUBMITTED", "New claim", "Waiting.", "/claims")));
        doThrow(new PermanentDeliveryException("Notification service refused the event: HTTP 400", null))
                .when(notificationClient).publish(eq(eventId), any());

        processor.processDue();

        OutboxEvent failed = outboxEventRepository.findByEventId(eventId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getLastError()).contains("HTTP 400");
        verify(expenseLockClient, never()).releaseClaim(anyLong());
    }

    @Test
    void permanentRefusalFailsImmediately() {
        Long id = enqueue(9007L);
        doThrow(new ExpenseServiceRejectedException("Expense service refused the release: HTTP 403", null))
                .when(expenseLockClient).releaseClaim(9007L);

        processor.processDue();

        assertThat(event(id).getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event(id).getAttempts()).isEqualTo(1);
        assertThat(event(id).getLastError()).contains("HTTP 403");
    }

    @Test
    void concurrentWorkersDeliverEachEventExactlyOnce() throws Exception {
        List<Long> claimIds = new ArrayList<>();
        for (long claimId = 9100; claimId < 9130; claimId++) {
            claimIds.add(claimId);
            enqueue(claimId);
        }
        Map<Long, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            deliveries.computeIfAbsent(invocation.getArgument(0), key -> new AtomicInteger()).incrementAndGet();
            return null;
        }).when(expenseLockClient).releaseClaim(anyLong());

        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                OutboxProcessor worker = newProcessor();
                results.add(pool.submit(() -> {
                    start.await();
                    int taken = 0;
                    for (int round = 0; round < 3; round++) {
                        taken += worker.processDue();
                    }
                    return taken;
                }));
            }
            start.countDown();
            int taken = 0;
            for (Future<Integer> result : results) {
                taken += result.get(60, TimeUnit.SECONDS);
            }
            assertThat(taken).isEqualTo(claimIds.size());
        } finally {
            pool.shutdownNow();
        }

        assertThat(deliveries).containsOnlyKeys(claimIds);
        assertThat(deliveries.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(outboxEventRepository.findAll()).allSatisfy(event ->
                assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETED));
    }

    private Long enqueue(Long claimId) {
        String eventId = new TransactionTemplate(transactionManager).execute(status ->
                claimOutbox.enqueueRelease(OutboxEventType.CLAIM_REJECTED, claimId, List.of(2L, 1L)));
        return outboxEventRepository.findByEventId(eventId).orElseThrow().getId();
    }

    private OutboxEvent event(Long id) {
        return outboxEventRepository.findById(id).orElseThrow();
    }

    private OutboxProcessor newProcessor() {
        return new OutboxProcessor(outboxEventRepository, claimRepository, expenseLockClient, notificationClient,
                objectMapper, transactionManager,
                clock, properties, metrics);
    }

    private LocalDateTime now() {
        return OutboxConfig.now(clock);
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant value) {
            instant = value;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
