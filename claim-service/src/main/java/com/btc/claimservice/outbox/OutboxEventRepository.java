package com.btc.claimservice.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every state change is a conditional UPDATE, so concurrent workers (threads or service instances) cannot both
 * take the same event, and a worker that lost its lease cannot overwrite the outcome of the one that took over.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    String DUE = "((e.status = com.btc.claimservice.outbox.OutboxStatus.PENDING and e.nextAttemptAt <= :now)"
            + " or (e.status = com.btc.claimservice.outbox.OutboxStatus.IN_PROGRESS and e.lockedUntil < :now))";
    String OWNED = " where e.id = :id and e.status = com.btc.claimservice.outbox.OutboxStatus.IN_PROGRESS"
            + " and e.lockedBy = :worker";

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findAllByClaimIdOrderById(Long claimId);

    long countByStatus(OutboxStatus status);

    long countByStatusAndAttemptsGreaterThan(OutboxStatus status, int attempts);

    /** Pending events that are due, plus in-progress ones whose worker's lease expired (crash or restart). */
    @Query("select e.id from OutboxEvent e where " + DUE + " order by e.id")
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable limit);

    /** Takes the event for {@code worker} until {@code leaseEnd}; returns 0 if it is not due or another worker won. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = com.btc.claimservice.outbox.OutboxStatus.IN_PROGRESS,"
            + " e.lockedBy = :worker, e.lockedUntil = :leaseEnd, e.attempts = e.attempts + 1, e.updatedAt = :now"
            + " where e.id = :id and " + DUE)
    int acquire(@Param("id") Long id, @Param("worker") String worker, @Param("now") LocalDateTime now,
                @Param("leaseEnd") LocalDateTime leaseEnd);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = com.btc.claimservice.outbox.OutboxStatus.COMPLETED,"
            + " e.completedAt = :now, e.updatedAt = :now, e.lockedBy = null, e.lockedUntil = null, e.lastError = null"
            + OWNED)
    int complete(@Param("id") Long id, @Param("worker") String worker, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = com.btc.claimservice.outbox.OutboxStatus.PENDING,"
            + " e.nextAttemptAt = :nextAttemptAt, e.lastError = :error, e.updatedAt = :now,"
            + " e.lockedBy = null, e.lockedUntil = null" + OWNED)
    int scheduleRetry(@Param("id") Long id, @Param("worker") String worker, @Param("now") LocalDateTime now,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("error") String error);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = com.btc.claimservice.outbox.OutboxStatus.FAILED,"
            + " e.lastError = :error, e.updatedAt = :now, e.lockedBy = null, e.lockedUntil = null" + OWNED)
    int fail(@Param("id") Long id, @Param("worker") String worker, @Param("now") LocalDateTime now,
             @Param("error") String error);
}
