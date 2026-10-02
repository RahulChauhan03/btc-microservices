package com.btc.claimservice.outbox;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records expense-lock release requests. {@link #enqueueRelease} must run inside the claim transaction, so the
 * claim change and its release request commit or roll back together. After commit the event is handed to a
 * background thread for prompt delivery; if that is not possible, the scheduled poll delivers it.
 */
@Slf4j
@Component
public class ClaimOutbox {

    /**
     * A rolled-back creation may have timed out waiting for its lock call while expense-service still completes
     * it; waiting well past the client read timeout before releasing makes the release land after that lock.
     */
    static final Duration ROLLBACK_RELEASE_DELAY = Duration.ofSeconds(30);

    private final OutboxEventRepository repository;
    private final OutboxProcessor processor;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TransactionTemplate newTransaction;
    private final ThreadPoolExecutor dispatcher = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(200), runnable -> {
                Thread thread = new Thread(runnable, "claim-outbox-dispatch");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    public ClaimOutbox(OutboxEventRepository repository, OutboxProcessor processor, OutboxProperties properties,
                       Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.processor = processor;
        this.properties = properties;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Adds a release request to the current (claim) transaction; fails if there is none. Returns the event id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueueRelease(OutboxEventType type, Long claimId, Collection<Long> expenseIds) {
        return save(type, claimId, expenseIds, OutboxConfig.now(clock));
    }

    /**
     * For compensation after the claim transaction rolled back (there is nothing left to join): records the
     * release in its own transaction, delayed by {@link #ROLLBACK_RELEASE_DELAY}. If even that fails, logs it.
     */
    public void enqueueReleaseAfterRollback(OutboxEventType type, Long claimId, Collection<Long> expenseIds) {
        try {
            newTransaction.executeWithoutResult(status ->
                    save(type, claimId, expenseIds, OutboxConfig.now(clock).plus(ROLLBACK_RELEASE_DELAY)));
        } catch (RuntimeException exception) {
            log.error("Could not record the expense-lock release of rolled-back claim {} (expenses {}); release it "
                    + "manually (docs/operations/phase-5-claim-outbox.md): {}", claimId, expenseIds,
                    OutboxProcessor.describe(exception));
        }
    }

    private String save(OutboxEventType type, Long claimId, Collection<Long> expenseIds, LocalDateTime firstAttemptAt) {
        LocalDateTime now = OutboxConfig.now(clock);
        OutboxEvent event = OutboxEvent.pending(UUID.randomUUID().toString(), type, claimId, expenseIds, now);
        event.scheduleFirstAttempt(firstAttemptAt);
        Long id = repository.save(event).getId();
        log.info("Claim outbox event {} recorded: {} of claim {}", event.getEventId(), type, claimId);
        if (properties.dispatchAfterCommit() && !firstAttemptAt.isAfter(now)) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.execute(() -> deliverQuietly(id));
                }
            });
        }
        return event.getEventId();
    }

    private void deliverQuietly(Long id) {
        try {
            processor.process(id);
        } catch (RuntimeException exception) {
            // Not lost: the event stays PENDING (or its lease expires) and the scheduled poll retries it.
            log.warn("Immediate delivery of claim outbox event id={} failed: {}", id, OutboxProcessor.describe(exception));
        }
    }

    @PreDestroy
    void shutdown() {
        dispatcher.shutdownNow();
    }
}
