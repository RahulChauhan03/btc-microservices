package com.btc.userservice.config;

import com.btc.userservice.entity.User;
import com.btc.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the initial administrator from environment-supplied credentials.
 * Never modifies an existing account, so rotating the admin password in the database is permanent.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminUserInitializer {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${btc.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${btc.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${btc.bootstrap.admin.phone:0000000000}")
    private String adminPhone;

    @Bean
    ApplicationRunner seedAdminUser() {
        return args -> seedAdmin();
    }

    void seedAdmin() {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped: BTC_ADMIN_EMAIL / BTC_ADMIN_PASSWORD not set");
            return;
        }
        if (userRepository.findByEmailIgnoreCase(adminEmail).isPresent()) {
            log.info("Admin bootstrap skipped: an account with the configured email already exists");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("BTC_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        userRepository.save(User.builder()
                .name("System Administrator")
                .email(adminEmail)
                .phone(adminPhone)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role("ADMIN")
                .build());
        log.info("Admin bootstrap: created initial administrator account");
    }
}
