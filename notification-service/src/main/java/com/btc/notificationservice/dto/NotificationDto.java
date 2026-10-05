package com.btc.notificationservice.dto;

import java.time.LocalDateTime;

public record NotificationDto(Long id, String type, String title, String message, String link,
                              LocalDateTime createdAt, boolean read) {
}
