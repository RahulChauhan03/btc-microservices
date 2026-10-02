package com.btc.claimservice.outbox;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    /** Source of the outbox's "now" (replaceable in tests). Timestamps never come from the database clock. */
    @Bean
    public Clock outboxClock() {
        return Clock.systemUTC();
    }

    /**
     * The current time as a {@link LocalDateTime} in the JVM's default zone. Hibernate binds LocalDateTime values
     * in that zone and the MySQL driver converts them to the connection zone (serverTimezone=UTC in config-repo),
     * so the stored values are true UTC whatever zone the service runs in. Building them in UTC instead would
     * store them shifted by the JVM's UTC offset.
     */
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }
}
