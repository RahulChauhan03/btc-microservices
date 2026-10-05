package com.btc.claimservice.outbox;

import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.client.NotificationClient;
import com.btc.claimservice.client.NotificationMessage;
import com.btc.claimservice.entity.ClaimStatus;
import com.btc.claimservice.exception.PermanentDeliveryException;
import com.btc.claimservice.repository.ClaimRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers outbox events to expense-service, at least once:
 * 1. take the event with a lease (conditional UPDATE, committed) so no other worker processes it meanwhile;
 * 2. call expense-service outside any transaction (DELETE of the claim's locks, which is idempotent and only
 *    clears expenses whose claim_id is this claim, so a repeat can never release another claim's expenses);
 * 3. mark it COMPLETED only after a successful response; otherwise schedule a retry with exponential backoff,
 *    or mark it FAILED (kept for diagnosis and manual retry) after the last attempt or a permanent refusal.
 * A worker that dies after step 1 leaves the event IN_PROGRESS; once its lease expires another worker takes it.
 */
@Slf4j
@Component
public class OutboxProcessor {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository repository;
    private final ClaimRepository claimRepository;
    private final ExpenseLockClient expenseLockClient;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final String workerId = "claim-service-" + UUID.randomUUID();

    public OutboxProcessor(OutboxEventRepository repository, ClaimRepository claimRepository,
                           ExpenseLockClient expenseLockClient, NotificationClient notificationClient,
                           ObjectMapper objectMapper, PlatformTransactionManager transactionManager,
                           Clock clock, OutboxProperties properties, OutboxMetrics metrics) {
        this.notificationClient = notificationClient;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.claimRepository = claimRepository;
        this.expenseLockClient = expenseLockClient;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Processes up to one batch of due events; returns how many this worker took. */
    public int processDue() {
        LocalDateTime now = now();
        List<Long> dueIds = transaction.execute(status ->
                repository.findDueIds(now, PageRequest.of(0, properties.batchSize())));
        int processed = 0;
        for (Long id : dueIds == null ? List.<Long>of() : dueIds) {
            if (process(id)) {
                processed++;
            }
        }
        return processed;
    }

    /** Delivers one event if it is due and not held by another worker; returns false if it was not taken. */
    public boolean process(Long id) {
        LocalDateTime now = now();
        OutboxEvent event = transaction.execute(status ->
                repository.acquire(id, workerId, now, now.plus(properties.lease())) == 1
                        ? repository.findById(id).orElse(null)
                        : null);
        if (event == null) {
            return false;
        }

        try {
            if (event.getEventType().isRelease()) {
                Optional<String> refusal = releaseRefusal(event);
                if (refusal.isPresent()) {
                    markFailed(event, refusal.get());
                    return true;
                }
                expenseLockClient.releaseClaim(event.getClaimId());
            } else {
                notificationClient.publish(event.getEventId(), readPayload(event));
            }
        } catch (PermanentDeliveryException exception) {
            markFailed(event, describe(exception));
            return true;
        } catch (RuntimeException exception) {
            retryOrFail(event, describe(exception));
            return true;
        }
        markCompleted(event);
        return true;
    }

    private NotificationMessage readPayload(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), NotificationMessage.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new PermanentDeliveryException("Notification payload is unreadable", exception);
        }
    }

    String workerId() {
        return workerId;
    }

    /** Defence in depth: never release the expenses of a claim that still exists and is not rejected. */
    private Optional<String> releaseRefusal(OutboxEvent event) {
        Optional<String> claimStatus = transaction.execute(status -> claimRepository.findStatusById(event.getClaimId()));
        if (claimStatus == null || claimStatus.isEmpty() || ClaimStatus.REJECTED.name().equals(claimStatus.get())) {
            return Optional.empty();
        }
        return Optional.of("Claim " + event.getClaimId() + " is " + claimStatus.get()
                + "; its expenses must stay locked, so the release was not sent");
    }

    private void markCompleted(OutboxEvent event) {
        if (update(() -> repository.complete(event.getId(), workerId, now()))) {
            metrics.recordCompleted();
            log.info("Claim outbox event {} ({} of claim {}) delivered on attempt {}",
                    event.getEventId(), event.getEventType(), event.getClaimId(), event.getAttempts());
        }
    }

    private void retryOrFail(OutboxEvent event, String error) {
        if (event.getAttempts() >= properties.maxAttempts()) {
            markFailed(event, error);
            return;
        }
        LocalDateTime nextAttemptAt = now().plus(properties.backoffAfter(event.getAttempts()));
        if (update(() -> repository.scheduleRetry(event.getId(), workerId, now(), nextAttemptAt, error))) {
            metrics.recordRetryScheduled();
            log.warn("Claim outbox event {} ({} of claim {}) attempt {}/{} failed: {}; next attempt in {}s",
                    event.getEventId(), event.getEventType(), event.getClaimId(), event.getAttempts(),
                    properties.maxAttempts(), error, properties.backoffAfter(event.getAttempts()).toSeconds());
        }
    }

    private void markFailed(OutboxEvent event, String error) {
        if (update(() -> repository.fail(event.getId(), workerId, now(), error))) {
            metrics.recordFailed();
            log.error("Claim outbox event {} ({} of claim {}) FAILED after {} attempt(s): {}. {} until it is retried "
                            + "(docs/operations/phase-5-claim-outbox.md)",
                    event.getEventId(), event.getEventType(), event.getClaimId(), event.getAttempts(), error,
                    event.getEventType().isRelease() ? "Expenses " + event.getExpenseIds() + " stay locked"
                            : "The notification is not delivered");
        }
    }

    /** Runs a conditional update; false means the lease was lost and another worker owns the outcome. */
    private boolean update(IntSupplier statement) {
        Integer updated = transaction.execute(status -> statement.getAsInt());
        if (updated == null || updated != 1) {
            log.warn("Claim outbox worker {} lost its lease; another worker took over the event", workerId);
            return false;
        }
        return true;
    }

    private LocalDateTime now() {
        return OutboxConfig.now(clock);
    }

    /** Exception classes and messages only (never request headers), bounded to fit the last_error column. */
    static String describe(Throwable failure) {
        List<String> parts = new ArrayList<>();
        for (Throwable current = failure; current != null && parts.size() < 3; current = current.getCause()) {
            parts.add(current.getClass().getSimpleName() + ": " + current.getMessage());
        }
        String description = String.join(" <- ", parts);
        return description.length() <= MAX_ERROR_LENGTH ? description : description.substring(0, MAX_ERROR_LENGTH);
    }
}
