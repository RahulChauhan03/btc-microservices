package com.btc.userservice.service;

import com.btc.userservice.audit.AuditService;
import com.btc.userservice.dto.ChangePasswordDto;
import com.btc.userservice.dto.ProfileUpdateDto;
import com.btc.userservice.dto.UserResponseDto;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.InvalidRequestException;
import com.btc.userservice.exception.TooManyRequestsException;
import com.btc.userservice.exception.UserNotFoundException;
import com.btc.userservice.repository.PasswordResetTokenRepository;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.CurrentUser;
import com.btc.userservice.web.RequestRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user's own profile. Identity always comes from the verified token. A password change requires
 * the current password, is rate-limited, revokes outstanding reset links and is audited.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProfileService {

    private static final int MAX_PASSWORD_ATTEMPTS = 10;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RequestRateLimiter rateLimiter;
    private final AuditService auditService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public UserResponseDto me(CurrentUser actor) {
        return userService.getUserById(actor.id(), actor);
    }

    public UserResponseDto update(CurrentUser actor, ProfileUpdateDto request) {
        User user = find(actor);
        user.setName(request.name().trim());
        user.setPhone(request.phone().trim());
        userRepository.save(user);
        return userService.getUserById(actor.id(), actor);
    }

    public void changePassword(CurrentUser actor, ChangePasswordDto request) {
        if (!rateLimiter.tryAcquire("change-password:" + actor.id(), MAX_PASSWORD_ATTEMPTS, ATTEMPT_WINDOW)) {
            throw new TooManyRequestsException("Too many attempts. Please try again later.");
        }
        User user = find(actor);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // 400, not 401: the session is valid; only the confirmation failed.
            throw new InvalidRequestException("Current password is incorrect");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidRequestException("Passwords do not match");
        }
        if (request.newPassword().isBlank() || request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Password must be 8 to 72 characters");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("Choose a password different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        resetTokenRepository.revokeOpenTokens(user.getId(), LocalDateTime.now());
        auditService.record(actor.id(), "USER_PASSWORD_CHANGED", "USER", actor.id(),
                "Password of user #%d changed by the user (current password verified)".formatted(actor.id()));
    }

    private User find(CurrentUser actor) {
        return userRepository.findById(actor.id())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + actor.id()));
    }
}
