package com.btc.userservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password-reset settings ({@code btc.password-reset.*}, mapped from BTC_* environment variables in
 * config-repo/user-service.properties). Email delivery itself uses Spring's {@code spring.mail.*}.
 *
 * @param frontendBaseUrl      origin of the Angular app; reset links are {@code <base>/reset-password?token=...}
 * @param mailFrom             sender address of reset emails
 * @param tokenTtl             lifetime of a reset link (default 15 minutes)
 * @param rateWindow           window for the limits below (default 15 minutes)
 * @param requestsPerEmail     reset emails per address per window (extra requests are silently ignored)
 * @param requestsPerClient    forgot-password requests per client IP per window (then HTTP 429)
 * @param resetAttemptsPerClient reset-password attempts per client IP per window (then HTTP 429)
 */
@ConfigurationProperties("btc.password-reset")
public record PasswordResetProperties(
        String frontendBaseUrl,
        String mailFrom,
        Duration tokenTtl,
        Duration rateWindow,
        Integer requestsPerEmail,
        Integer requestsPerClient,
        Integer resetAttemptsPerClient) {

    public PasswordResetProperties {
        frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim().replaceAll("/+$", "");
        mailFrom = mailFrom == null ? "" : mailFrom.trim();
        tokenTtl = tokenTtl == null ? Duration.ofMinutes(15) : tokenTtl;
        rateWindow = rateWindow == null ? Duration.ofMinutes(15) : rateWindow;
        requestsPerEmail = requestsPerEmail == null ? 3 : requestsPerEmail;
        requestsPerClient = requestsPerClient == null ? 10 : requestsPerClient;
        resetAttemptsPerClient = resetAttemptsPerClient == null ? 20 : resetAttemptsPerClient;
        if (tokenTtl.compareTo(Duration.ofMinutes(5)) < 0 || tokenTtl.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalArgumentException("btc.password-reset.token-ttl must be between 5 minutes and 2 hours");
        }
    }
}
