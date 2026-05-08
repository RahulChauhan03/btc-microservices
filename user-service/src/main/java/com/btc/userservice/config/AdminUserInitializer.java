package com.btc.userservice.config;

import com.btc.userservice.entity.User;
import com.btc.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminUserInitializer {

    private static final String ADMIN_EMAIL = "admin@btcsystem.com";
    private static final String ADMIN_PASSWORD = "Password@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner seedAdminUser() {
        return args -> userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)
                .or(() -> userRepository.findByEmail(ADMIN_EMAIL))
                .ifPresentOrElse(user -> {
                    if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
                        user.setRole("ADMIN");
                        userRepository.save(user);
                    }
                    if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()
                            || !passwordEncoder.matches(ADMIN_PASSWORD, user.getPasswordHash())) {
                        user.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
                        userRepository.save(user);
                    }
                }, () -> userRepository.save(User.builder()
                        .name("System Administrator")
                        .email(ADMIN_EMAIL)
                        .phone("9999999999")
                        .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                        .role("ADMIN")
                        .build()));
    }
}
