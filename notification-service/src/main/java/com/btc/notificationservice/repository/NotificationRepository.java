package com.btc.notificationservice.repository;

import com.btc.notificationservice.dto.NotificationDto;
import com.btc.notificationservice.entity.Notification;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Visibility is one rule, applied in every query: a user's own notifications, plus (administrators only) the
 * ones addressed to all administrators that they did not cause themselves.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    String VISIBLE = "((n.audience = 'USER' and n.recipientId = :userId)"
            + " or (n.audience = 'ADMINS' and :admin = true and (n.actorId is null or n.actorId <> :userId)))";

    boolean existsByEventId(String eventId);

    @Query(value = "select new com.btc.notificationservice.dto.NotificationDto(n.id, n.type, n.title, n.message, n.link,"
            + " n.createdAt, case when r.id is not null then true else false end) from Notification n"
            + " left join NotificationRead r on r.id.notificationId = n.id and r.id.userId = :userId"
            + " where " + VISIBLE + " and (:unreadOnly = false or r.id is null) order by n.createdAt desc, n.id desc",
            countQuery = "select count(n) from Notification n"
            + " left join NotificationRead r on r.id.notificationId = n.id and r.id.userId = :userId"
            + " where " + VISIBLE + " and (:unreadOnly = false or r.id is null)")
    Page<NotificationDto> findVisible(@Param("userId") Long userId, @Param("admin") boolean admin,
                                      @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query("select count(n) from Notification n where " + VISIBLE + " and not exists"
            + " (select 1 from NotificationRead r where r.id.notificationId = n.id and r.id.userId = :userId)")
    long countUnread(@Param("userId") Long userId, @Param("admin") boolean admin);

    @Query("select count(n) > 0 from Notification n where n.id = :id and " + VISIBLE)
    boolean isVisible(@Param("id") Long id, @Param("userId") Long userId, @Param("admin") boolean admin);

    /** Marks every visible, unread notification as read; idempotent (plain SQL for an INSERT ... SELECT). */
    @Modifying
    @Query(nativeQuery = true, value = "insert into notification_reads (notification_id, user_id, read_at)"
            + " select n.id, :userId, :now from notifications n where"
            + " ((n.audience = 'USER' and n.recipient_id = :userId)"
            + " or (n.audience = 'ADMINS' and :admin = true and (n.actor_id is null or n.actor_id <> :userId)))"
            + " and not exists (select 1 from notification_reads r where r.notification_id = n.id and r.user_id = :userId)")
    int markAllRead(@Param("userId") Long userId, @Param("admin") boolean admin, @Param("now") LocalDateTime now);
}
