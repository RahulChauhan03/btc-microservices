package com.btc.notificationservice.service;

import com.btc.notificationservice.dto.NotificationDto;
import com.btc.notificationservice.dto.NotificationEventDto;
import com.btc.notificationservice.entity.Notification;
import com.btc.notificationservice.entity.NotificationRead;
import com.btc.notificationservice.exception.InvalidRequestException;
import com.btc.notificationservice.exception.NotificationNotFoundException;
import com.btc.notificationservice.repository.NotificationReadRepository;
import com.btc.notificationservice.repository.NotificationRepository;
import com.btc.notificationservice.security.CurrentUser;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications are created only from service events and are visible only to their addressee (see
 * NotificationRepository.VISIBLE); the caller's identity always comes from their verified token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository readRepository;

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(CurrentUser actor, boolean unreadOnly, Pageable pageable) {
        return notificationRepository.findVisible(actor.id(), actor.admin(), unreadOnly, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser actor) {
        return notificationRepository.countUnread(actor.id(), actor.admin());
    }

    /** Idempotent. Not found (404) for notifications the caller cannot see, so ids reveal nothing. */
    public void markRead(CurrentUser actor, Long id) {
        if (!notificationRepository.isVisible(id, actor.id(), actor.admin())) {
            throw new NotificationNotFoundException("Notification not found with id: " + id);
        }
        NotificationRead.Key key = new NotificationRead.Key(id, actor.id());
        if (readRepository.existsById(key)) {
            return;
        }
        try {
            readRepository.saveAndFlush(new NotificationRead(key, LocalDateTime.now()));
        } catch (DataIntegrityViolationException alreadyRead) {
            // marked read concurrently: same outcome
        }
    }

    @Transactional
    public int markAllRead(CurrentUser actor) {
        return notificationRepository.markAllRead(actor.id(), actor.admin(), LocalDateTime.now());
    }

    /**
     * Stores a service event once; returns false for a repeat delivery of the same eventId (at-least-once
     * delivery from the claim-service outbox makes repeats normal).
     */
    public boolean record(NotificationEventDto event) {
        boolean toUser = Notification.AUDIENCE_USER.equals(event.audience());
        if (toUser && event.recipientId() == null) {
            throw new InvalidRequestException("recipientId is required for audience USER");
        }
        if (notificationRepository.existsByEventId(event.eventId())) {
            return false;
        }
        try {
            notificationRepository.saveAndFlush(Notification.builder()
                    .eventId(event.eventId())
                    .audience(event.audience())
                    .recipientId(toUser ? event.recipientId() : null)
                    .actorId(event.actorId())
                    .type(event.type())
                    .title(event.title())
                    .message(event.message())
                    .link(event.link())
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
        log.info("Notification {} recorded for {} ({})", event.type(), event.audience(), event.eventId());
        return true;
    }
}
