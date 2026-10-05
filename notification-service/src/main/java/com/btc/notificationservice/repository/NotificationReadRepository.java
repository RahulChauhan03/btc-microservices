package com.btc.notificationservice.repository;

import com.btc.notificationservice.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, NotificationRead.Key> {
}
