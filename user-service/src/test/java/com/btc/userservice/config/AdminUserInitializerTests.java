package com.btc.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.btc.userservice.entity.User;
import com.btc.userservice.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminUserInitializerTests {

    private static final String EMAIL = "bootstrap-admin@example.com";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AdminUserInitializer initializer = new AdminUserInitializer(userRepository, passwordEncoder);

    @Test
    void createsAdminWhenAbsent() {
        String password = UUID.randomUUID().toString();
        configure(EMAIL, password);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        initializer.seedAdmin();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void neverOverwritesExistingAccount() {
        User existing = User.builder().email(EMAIL).passwordHash("existing-hash").role("EMPLOYEE").build();
        configure(EMAIL, UUID.randomUUID().toString());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        initializer.seedAdmin();

        verify(userRepository, never()).save(any());
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
        assertThat(existing.getRole()).isEqualTo("EMPLOYEE");
    }

    @Test
    void skipsWhenCredentialsNotConfigured() {
        configure("", "");

        initializer.seedAdmin();

        verifyNoInteractions(userRepository);
    }

    @Test
    void refusesWeakBootstrapPassword() {
        configure(EMAIL, "short");
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(initializer::seedAdmin).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(initializer, "adminEmail", email);
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
        ReflectionTestUtils.setField(initializer, "adminPhone", "0000000000");
    }
}
