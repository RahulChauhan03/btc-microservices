package com.btc.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sent by other services (SERVICE token). recipientId is required for audience USER, ignored for ADMINS. */
public record NotificationEventDto(
        @NotBlank @Size(max = 36) String eventId,
        @NotBlank @Pattern(regexp = "USER|ADMINS") String audience,
        Long recipientId,
        Long actorId,
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z_]+") String type,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 500) String message,
        @Size(max = 200) @Pattern(regexp = "/[A-Za-z0-9/_-]*") String link) {
}
