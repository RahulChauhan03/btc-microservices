package com.btc.userservice.service.impl;

import com.btc.userservice.audit.AuditService;
import com.btc.userservice.config.PasswordResetProperties;
import com.btc.userservice.dto.ResetPasswordRequestDto;
import com.btc.userservice.entity.PasswordResetToken;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.ExpiredResetTokenException;
import com.btc.userservice.exception.InvalidRequestException;
import com.btc.userservice.exception.InvalidResetTokenException;
import com.btc.userservice.exception.ServiceUnavailableException;
import com.btc.userservice.exception.TooManyRequestsException;
import com.btc.userservice.mail.PasswordResetMailer;
import com.btc.userservice.repository.PasswordResetTokenRepository;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.ResetTokens;
import com.btc.userservice.service.PasswordResetService;
import com.btc.userservice.web.RequestRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Self-service password reset.
 * - Request: for an existing account, supersedes its open tokens, stores the hash of a new 256-bit token and
 *   emails the link after commit. Unknown addresses get exactly the same response. Limits: per client IP
 *   (HTTP 429) and per address (silently no further emails, so the limit reveals nothing).
 * - Reset: the token is consumed with a conditional update in the same transaction as the password change,
 *   so it works once even under concurrent use; all other open tokens of the user are revoked.
 * Logs carry user ids only, never addresses, tokens, links or passwords.
 */
@Slf4j
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    static final String INVALID_LINK = "This password reset link is invalid or has already been used.";
    static final String EXPIRED_LINK = "This password reset link has expired. Please request a new one.";
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final PasswordResetProperties properties;
    private final RequestRateLimiter rateLimiter;
    private final Clock clock;
    private final TransactionTemplate newTransaction;
    private final AuditService auditService;

    public PasswordResetServiceImpl(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                    PasswordEncoder passwordEncoder, PasswordResetMailer mailer,
                                    PasswordResetProperties properties, RequestRateLimiter rateLimiter,
                                    Clock passwordResetClock, PlatformTransactionManager transactionManager,
                                    AuditService auditService) {
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = passwordResetClock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public void requestReset(String rawEmail, String clientKey) {
        if (!rateLimiter.tryAcquire("forgot:" + clientKey, properties.requestsPerClient(), properties.rateWindow())) {
            throw new TooManyRequestsException("Too many password reset requests. Please try again later.");
        }
        if (!mailer.isConfigured()) {
            // Independent of the address, so it reveals nothing about accounts.
            throw new ServiceUnavailableException(
                    "Password reset is temporarily unavailable. Please contact your administrator.");
        }

        String email = rawEmail.trim();
        boolean withinAddressLimit = rateLimiter.tryAcquire("forgot-email:" + email.toLowerCase(Locale.ROOT),
                properties.requestsPerEmail(), properties.rateWindow());
        Optional<User> account = userRepository.findByEmailIgnoreCase(email);
        if (account.isEmpty() || !withinAddressLimit) {
            return;
        }

        User user = account.get();
        LocalDateTime now = now();
        tokenRepository.revokeOpenTokens(user.getId(), now);
        String token = ResetTokens.newToken();
        Long tokenId = tokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(ResetTokens.hash(token))
                .createdAt(now)
                .expiresAt(now.plus(properties.tokenTtl()))
                .build()).getId();
        log.info("Password reset requested for user id {}", user.getId());

        String recipient = user.getEmail();
        afterCommit(() -> mailer.sendAsync(recipient, token, () -> revokeUndeliverable(tokenId, user.getId())));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDto request, String clientKey) {
        if (!rateLimiter.tryAcquire("reset:" + clientKey, properties.resetAttemptsPerClient(), properties.rateWindow())) {
            throw new TooManyRequestsException("Too many attempts. Please try again later.");
        }
        String newPassword = request.getNewPassword();
        if (!newPassword.equals(request.getConfirmPassword())) {
            throw new InvalidRequestException("Passwords do not match");
        }
        if (newPassword.isBlank() || newPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new InvalidRequestException("Password must be 8 to 72 characters");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(ResetTokens.hash(request.getToken()))
                .orElseThrow(() -> new InvalidResetTokenException(INVALID_LINK));
        LocalDateTime now = now();
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            throw new InvalidResetTokenException(INVALID_LINK);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ExpiredResetTokenException(EXPIRED_LINK);
        }
        if (tokenRepository.consume(token.getId(), now) != 1) {
            throw new InvalidResetTokenException(INVALID_LINK); // used concurrently a moment ago
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidResetTokenException(INVALID_LINK));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.revokeOpenTokens(user.getId(), now);
        auditService.record(user.getId(), "USER_PASSWORD_RESET", "USER", user.getId(),
                "Password of user #%d reset with an emailed link".formatted(user.getId()));
        log.info("Password reset completed for user id {}", user.getId());
    }

    private void revokeUndeliverable(Long tokenId, Long userId) {
        try {
            newTransaction.executeWithoutResult(status -> tokenRepository.revoke(tokenId, now()));
            log.warn("Revoked the undeliverable password reset link of user id {}", userId);
        } catch (RuntimeException exception) {
            log.error("Could not revoke an undeliverable password reset link of user id {} ({})", userId,
                    exception.getClass().getSimpleName());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** After the surrounding transaction commits (immediately when there is none). */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
