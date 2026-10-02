package com.btc.claimservice.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Micrometer meters (visible at /actuator/metrics when that endpoint is exposed):
 * - {@code btc.claim.outbox.events{state=pending|retrying|in_progress|failed}}: current counts, refreshed every
 *   poll; {@code retrying} is the subset of {@code pending} that has failed at least once. Alert on failed > 0.
 * - {@code btc.claim.outbox.deliveries{outcome=completed|retry_scheduled|failed}}: attempts since startup.
 */
@Slf4j
@Component
public class OutboxMetrics {

    private final OutboxEventRepository repository;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong retrying = new AtomicLong();
    private final AtomicLong inProgress = new AtomicLong();
    private final AtomicLong failed = new AtomicLong(-1);
    private final Counter completedDeliveries;
    private final Counter scheduledRetries;
    private final Counter failedDeliveries;

    public OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
        this.repository = repository;
        gauge(registry, "pending", pending);
        gauge(registry, "retrying", retrying);
        gauge(registry, "in_progress", inProgress);
        gauge(registry, "failed", failed);
        completedDeliveries = counter(registry, "completed");
        scheduledRetries = counter(registry, "retry_scheduled");
        failedDeliveries = counter(registry, "failed");
    }

    public void refresh() {
        pending.set(repository.countByStatus(OutboxStatus.PENDING));
        retrying.set(repository.countByStatusAndAttemptsGreaterThan(OutboxStatus.PENDING, 0));
        inProgress.set(repository.countByStatus(OutboxStatus.IN_PROGRESS));
        long failedNow = repository.countByStatus(OutboxStatus.FAILED);
        long failedBefore = failed.getAndSet(failedNow);
        if (failedNow > 0 && failedNow != failedBefore) {
            log.warn("{} claim outbox event(s) are FAILED and need manual recovery "
                    + "(docs/operations/phase-5-claim-outbox.md)", failedNow);
        }
    }

    void recordCompleted() {
        completedDeliveries.increment();
    }

    void recordRetryScheduled() {
        scheduledRetries.increment();
    }

    void recordFailed() {
        failedDeliveries.increment();
    }

    private static void gauge(MeterRegistry registry, String state, AtomicLong value) {
        Gauge.builder("btc.claim.outbox.events", value, current -> Math.max(current.get(), 0))
                .description("Claim outbox events by state")
                .tag("state", state)
                .register(registry);
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("btc.claim.outbox.deliveries")
                .description("Claim outbox delivery attempts by outcome")
                .tag("outcome", outcome)
                .register(registry);
    }
}
