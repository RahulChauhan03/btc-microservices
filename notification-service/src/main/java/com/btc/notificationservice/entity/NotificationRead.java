package com.btc.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_reads", indexes = @Index(name = "idx_notification_reads_user", columnList = "user_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRead {

    @EmbeddedId
    private Key id;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "notification_id", nullable = false)
        private Long notificationId;

        @Column(name = "user_id", nullable = false)
        private Long userId;
    }
}
