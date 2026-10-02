package com.btc.claimservice.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox delivery settings ({@code btc.outbox.*}; the poll interval and scheduling switch are read by
 * {@link OutboxScheduler}). Defaults: 10 attempts with 5s, 10s, 20s ... backoff capped
 * at 10 minutes (about 40 minutes in total) before an event is marked FAILED for manual recovery.
 *
 * @param batchSize           events taken per poll
 * @param maxAttempts         attempts before an event is FAILED
 * @param initialBackoff      delay after the first failed attempt; doubled per attempt
 * @param maxBackoff          upper bound of the delay
 * @param lease               how long a worker owns an IN_PROGRESS event; longer than one HTTP call with timeouts
 * @param dispatchAfterCommit try to deliver a new event right after its transaction commits
 */
@ConfigurationProperties("btc.outbox")
public record OutboxProperties(
        Integer batchSize,
        Integer maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration lease,
        Boolean dispatchAfterCommit) {

    public OutboxProperties {
        batchSize = batchSize == null ? 20 : batchSize;
        maxAttempts = maxAttempts == null ? 10 : maxAttempts;
        initialBackoff = initialBackoff == null ? Duration.ofSeconds(5) : initialBackoff;
        maxBackoff = maxBackoff == null ? Duration.ofMinutes(10) : maxBackoff;
        lease = lease == null ? Duration.ofMinutes(2) : lease;
        dispatchAfterCommit = dispatchAfterCommit == null || dispatchAfterCommit;
        if (batchSize < 1 || maxAttempts < 1 || initialBackoff.isNegative() || maxBackoff.compareTo(initialBackoff) < 0
                || lease.compareTo(Duration.ofSeconds(30)) < 0) {
            throw new IllegalArgumentException("Invalid btc.outbox settings");
        }
    }

    /** Delay before attempt {@code attempts + 1}: initialBackoff * 2^(attempts - 1), at most maxBackoff. */
    public Duration backoffAfter(int attempts) {
        int doublings = Math.min(Math.max(attempts - 1, 0), 30);
        Duration delay = initialBackoff.multipliedBy(1L << doublings);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }
}
