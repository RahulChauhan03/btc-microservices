-- notification-service schema. Notifications are written only by other services (SERVICE token) and read by the
-- users they are addressed to: one user (audience USER) or every administrator (audience ADMINS).
-- event_id makes delivery idempotent: the claim-service outbox may deliver the same event more than once.

CREATE TABLE IF NOT EXISTS notifications (
  id bigint NOT NULL AUTO_INCREMENT,
  event_id varchar(36) NOT NULL,
  audience varchar(20) NOT NULL,
  recipient_id bigint DEFAULT NULL,
  actor_id bigint DEFAULT NULL,
  type varchar(40) NOT NULL,
  title varchar(150) NOT NULL,
  message varchar(500) NOT NULL,
  link varchar(200) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notifications_event_id (event_id),
  KEY idx_notifications_recipient (recipient_id, created_at),
  KEY idx_notifications_audience (audience, created_at)
) ENGINE=InnoDB;

-- Read state per user (also covers notifications shared by all administrators).
CREATE TABLE IF NOT EXISTS notification_reads (
  notification_id bigint NOT NULL,
  user_id bigint NOT NULL,
  read_at datetime(6) NOT NULL,
  PRIMARY KEY (notification_id, user_id),
  KEY idx_notification_reads_user (user_id),
  CONSTRAINT fk_notification_reads_notification FOREIGN KEY (notification_id) REFERENCES notifications (id) ON DELETE CASCADE
) ENGINE=InnoDB;
