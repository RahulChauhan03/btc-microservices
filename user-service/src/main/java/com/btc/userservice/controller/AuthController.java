package com.btc.userservice.controller;

import com.btc.userservice.dto.AuthLoginRequestDto;
import com.btc.userservice.dto.AuthResponseDto;
import com.btc.userservice.dto.ForgotPasswordRequestDto;
import com.btc.userservice.dto.MessageResponseDto;
import com.btc.userservice.dto.ResetPasswordRequestDto;
import com.btc.userservice.service.AuthenticationService;
import com.btc.userservice.service.PasswordResetService;
import com.btc.userservice.web.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Identical for every address, so the response never reveals whether an account exists. */
    static final String FORGOT_PASSWORD_RESPONSE =
            "If an account exists for that email, you will receive password-reset instructions.";

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody AuthLoginRequestDto requestDto) {
        return ResponseEntity.ok(authenticationService.login(requestDto));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponseDto> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto requestDto,
                                                             HttpServletRequest request) {
        passwordResetService.requestReset(requestDto.getEmail(), RequestRateLimiter.clientKey(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResponseDto(FORGOT_PASSWORD_RESPONSE));
    }

    /** Does not sign the user in: they log in with the new password afterwards. */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponseDto> resetPassword(@Valid @RequestBody ResetPasswordRequestDto requestDto,
                                                            HttpServletRequest request) {
        passwordResetService.resetPassword(requestDto, RequestRateLimiter.clientKey(request));
        return ResponseEntity.ok(new MessageResponseDto("Your password has been reset. You can now sign in."));
    }
}
