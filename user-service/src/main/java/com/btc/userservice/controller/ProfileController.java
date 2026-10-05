package com.btc.userservice.controller;

import com.btc.userservice.dto.ChangePasswordDto;
import com.btc.userservice.dto.ProfileUpdateDto;
import com.btc.userservice.dto.UserResponseDto;
import com.btc.userservice.security.CurrentUser;
import com.btc.userservice.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own profile ("me" is always the token's subject; no id is accepted from the client). */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<UserResponseDto> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(profileService.me(CurrentUser.from(jwt)));
    }

    @PutMapping
    public ResponseEntity<UserResponseDto> update(@Valid @RequestBody ProfileUpdateDto request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(profileService.update(CurrentUser.from(jwt), request));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDto request, @AuthenticationPrincipal Jwt jwt) {
        profileService.changePassword(CurrentUser.from(jwt), request);
        return ResponseEntity.noContent().build();
    }
}
