package com.btc.claimservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A request to release the expense locks of a claim, written in the same transaction as the claim change that
 * makes the release necessary (see V4__claim_outbox.sql). Holds identifiers only: no tokens or personal data.
 * State changes after creation are conditional bulk updates in {@link OutboxEventRepository}.
 */
@Entity
@Table(name = "claim_outbox", indexes = {
        @Index(name = "idx_claim_outbox_status_next_attempt", columnList = "status, next_attempt_at"),
        @Index(name = "idx_claim_outbox_claim_id", columnList = "claim_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 40)
    private OutboxEventType eventType;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    /** Comma-separated expense ids the claim covered, for diagnosis and manual recovery. */
    @Column(name = "expense_ids", nullable = false, length = 2000)
    private String expenseIds;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    /** Delivery attempts started so far (incremented when a worker takes the event). */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    /** End of the current worker's lease; an IN_PROGRESS event past it was abandoned and is taken again. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** NOTIFICATION events only: the message for notification-service, as JSON. */
    @Column(length = 2000)
    private String payload;

    static OutboxEvent pending(String eventId, OutboxEventType type, Long claimId, Collection<Long> expenseIds,
                               LocalDateTime now) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.eventType = type;
        event.claimId = claimId;
        event.expenseIds = expenseIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        event.status = OutboxStatus.PENDING;
        event.attempts = 0;
        event.nextAttemptAt = now;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    static OutboxEvent notification(String eventId, Long claimId, String payload, LocalDateTime now) {
        OutboxEvent event = pending(eventId, OutboxEventType.NOTIFICATION, claimId, List.of(), now);
        event.payload = payload;
        return event;
    }

    void scheduleFirstAttempt(LocalDateTime firstAttemptAt) {
        nextAttemptAt = firstAttemptAt;
    }

    public List<Long> expenseIdList() {
        return expenseIds.isEmpty() ? List.of() : Arrays.stream(expenseIds.split(",")).map(Long::valueOf).toList();
    }
}
