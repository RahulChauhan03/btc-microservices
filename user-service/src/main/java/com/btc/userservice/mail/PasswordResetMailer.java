package com.btc.userservice.mail;

import com.btc.userservice.config.PasswordResetProperties;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends password-reset emails through Spring's JavaMailSender (SMTP settings from BTC_MAIL_* variables).
 * Delivery runs on a background thread, after the request has been answered, so response time and outcome
 * are the same whether or not an account exists. Never logs the token or the reset link.
 */
@Slf4j
@Component
public class PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final PasswordResetProperties properties;
    private final List<String> missingSettings;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), runnable -> {
                Thread thread = new Thread(runnable, "password-reset-mail");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public PasswordResetMailer(ObjectProvider<JavaMailSender> mailSender, PasswordResetProperties properties,
                               @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.missingSettings = missingSettings(mailHost, properties);
        if (!missingSettings.isEmpty()) {
            log.warn("Password reset emails are disabled until these settings are provided: {} "
                    + "(see .env.example); forgot-password requests will answer 503", missingSettings);
        }
    }

    public boolean isConfigured() {
        return missingSettings.isEmpty() && mailSender.getIfAvailable() != null;
    }

    /**
     * Queues the email. {@code onFailure} runs (on the mail thread) if it cannot be delivered, so the caller
     * can revoke the unusable token. Returns false if the queue is full (the caller treats that as a failure).
     */
    public boolean sendAsync(String recipient, String token, Runnable onFailure) {
        String link = resetLink(token);
        try {
            executor.execute(() -> {
                try {
                    send(recipient, link);
                } catch (Exception exception) {
                    // Exception class only: messages may echo addresses or server responses.
                    log.warn("Password reset email could not be delivered ({})", exception.getClass().getSimpleName());
                    onFailure.run();
                }
            });
            return true;
        } catch (RuntimeException rejected) {
            log.warn("Password reset email queue is full; request dropped");
            onFailure.run();
            return false;
        }
    }

    String resetLink(String token) {
        return UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path("/reset-password")
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    void send(String recipient, String link) throws MessagingException {
        JavaMailSender sender = mailSender.getObject();
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(properties.mailFrom());
        helper.setTo(recipient);
        helper.setSubject("Reset your BTC Flow password");
        long minutes = properties.tokenTtl().toMinutes();
        helper.setText(PasswordResetEmail.text(link, minutes), PasswordResetEmail.html(link, minutes));
        sender.send(message);
    }

    private static List<String> missingSettings(String mailHost, PasswordResetProperties properties) {
        List<String> missing = new ArrayList<>();
        if (mailHost == null || mailHost.isBlank()) {
            missing.add("BTC_MAIL_HOST");
        }
        if (properties.mailFrom().isBlank()) {
            missing.add("BTC_MAIL_FROM");
        }
        if (!isHttpUrl(properties.frontendBaseUrl())) {
            missing.add("BTC_FRONTEND_BASE_URL");
        }
        return List.copyOf(missing);
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
