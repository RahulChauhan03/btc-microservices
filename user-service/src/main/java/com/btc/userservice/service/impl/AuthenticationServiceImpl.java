package com.btc.userservice.service.impl;

import com.btc.userservice.dto.AuthLoginRequestDto;
import com.btc.userservice.dto.AuthResponseDto;
import com.btc.userservice.dto.AuthUserDto;
import com.btc.userservice.entity.User;
import com.btc.userservice.exception.InvalidCredentialsException;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.service.AuthenticationService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final long TOKEN_EXPIRY_SECONDS = 60L * 60L * 8L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    @Value("${btc.security.jwt.issuer}")
    private String issuer;

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
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(TOKEN_EXPIRY_SECONDS))
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim("department", user.getDepartment())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
