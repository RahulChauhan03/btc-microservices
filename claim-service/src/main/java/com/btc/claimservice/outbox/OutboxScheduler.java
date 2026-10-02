package com.btc.claimservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Polls for due outbox events, including ones left behind by a restart or crash. Disable with
 * {@code btc.outbox.scheduling-enabled=false} (tests drive {@link OutboxProcessor} directly).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "btc.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxProcessor processor;
    private final OutboxMetrics metrics;

    @Scheduled(initialDelayString = "${btc.outbox.poll-interval:PT5S}", fixedDelayString = "${btc.outbox.poll-interval:PT5S}")
    public void poll() {
        try {
            processor.processDue();
            metrics.refresh();
        } catch (RuntimeException exception) {
            // e.g. the database is briefly unreachable; the next poll tries again.
            log.warn("Claim outbox poll failed: {}", OutboxProcessor.describe(exception));
        }
    }
}
