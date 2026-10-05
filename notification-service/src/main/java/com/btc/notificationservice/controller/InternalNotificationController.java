package com.btc.notificationservice.controller;

import com.btc.notificationservice.dto.NotificationEventDto;
import com.btc.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event intake for other services. Requires a SERVICE token (SecurityConfig); the API gateway never routes
 * /notifications/internal/**. Idempotent per eventId: 201 when stored, 200 for a repeat delivery.
 */
@RestController
@RequestMapping("/notifications/internal/events")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<Void> record(@Valid @RequestBody NotificationEventDto event) {
        return ResponseEntity.status(notificationService.record(event) ? HttpStatus.CREATED : HttpStatus.OK).build();
    }
}
