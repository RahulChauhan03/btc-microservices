package com.btc.notificationservice.controller;

import com.btc.notificationservice.dto.NotificationDto;
import com.btc.notificationservice.dto.UnreadCountDto;
import com.btc.notificationservice.exception.InvalidRequestException;
import com.btc.notificationservice.security.CurrentUser;
import com.btc.notificationservice.service.NotificationService;
import com.btc.notificationservice.web.PageRequests;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's notifications only, newest first. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @AuthenticationPrincipal Jwt jwt) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequests.toResponse(notificationService.list(CurrentUser.from(jwt), unreadOnly, PageRequest.of(page, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountDto> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new UnreadCountDto(notificationService.unreadCount(CurrentUser.from(jwt))));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        notificationService.markRead(CurrentUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(CurrentUser.from(jwt))));
    }
}
