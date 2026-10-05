package com.btc.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The fields users may change on their own profile. Email (the sign-in identity) and role are not among them. */
public record ProfileUpdateDto(
        @NotBlank(message = "Name is required") @Size(max = 255) String name,
        @NotBlank(message = "Phone is required") @Size(max = 255) String phone) {
}
