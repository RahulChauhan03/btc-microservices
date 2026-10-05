package com.btc.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A message for one user (audience USER) or for every administrator (audience ADMINS). */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient", columnList = "recipient_id, created_at"),
        @Index(name = "idx_notifications_audience", columnList = "audience, created_at")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    public static final String AUDIENCE_USER = "USER";
    public static final String AUDIENCE_ADMINS = "ADMINS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(nullable = false, length = 20)
    private String audience;

    @Column(name = "recipient_id")
    private Long recipientId;

    /** Who caused it; an administrator is not notified about their own actions. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** In-app route to open, e.g. /claims. */
    @Column(length = 200)
    private String link;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
