package com.btc.userservice.service.impl;

import com.btc.userservice.dto.AuthLoginRequestDto;
import com.btc.userservice.dto.AuthResponseDto;
import com.btc.userservice.dto.AuthUserDto;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.InvalidCredentialsException;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.service.AuthenticationService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final long TOKEN_EXPIRY_SECONDS = 60L * 60L * 8L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResponseDto login(AuthLoginRequestDto requestDto) {
        User user = userRepository.findByEmailIgnoreCase(requestDto.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()
                || !passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        AuthUserDto authUser = AuthUserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() == null || user.getRole().isBlank() ? "EMPLOYEE" : user.getRole())
                .department("Operations")
                .build();

        return AuthResponseDto.builder()
                .token(generateToken(authUser))
                .expiresIn(TOKEN_EXPIRY_SECONDS)
                .user(authUser)
                .build();
    }

    private String generateToken(AuthUserDto user) {
        long expiresAt = Instant.now().getEpochSecond() + TOKEN_EXPIRY_SECONDS;
        String header = encode("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = encode(String.format(
                "{\"sub\":%d,\"name\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"department\":\"%s\",\"exp\":%d}",
                user.getId(),
                escapeJson(user.getName()),
                escapeJson(user.getEmail()),
                escapeJson(user.getRole()),
                escapeJson(user.getDepartment()),
                expiresAt
        ));

        return header + "." + payload + ".signature";
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
