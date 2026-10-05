package com.btc.userservice.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfig {

    /**
     * JVM default zone: LocalDateTime values are bound by Hibernate in that zone and stored as UTC by the driver
     * (serverTimezone=UTC), so expiry comparisons stay correct on any server time zone.
     */
    @Bean
    public Clock passwordResetClock() {
        return Clock.systemDefaultZone();
    }
}
