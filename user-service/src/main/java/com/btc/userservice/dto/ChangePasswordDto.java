package com.btc.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDto(
        @NotBlank(message = "Current password is required") String currentPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String newPassword,
        @NotBlank(message = "Password confirmation is required") String confirmPassword) {

    @Override
    public String toString() {
        return "ChangePasswordDto[redacted]";
    }
}
