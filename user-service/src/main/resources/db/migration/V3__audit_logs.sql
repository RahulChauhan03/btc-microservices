-- Phase 7: audit log of security-relevant actions in this service. Additive: one new table.

-- Written by user-service in the same transaction as the audited change; never updated or deleted by the app.
CREATE TABLE IF NOT EXISTS audit_logs (
  id bigint NOT NULL AUTO_INCREMENT,
  actor_id bigint NOT NULL,
  action varchar(60) NOT NULL,
  target_type varchar(40) NOT NULL,
  target_id bigint NOT NULL,
  summary varchar(500) NOT NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_logs_created (created_at),
  KEY idx_audit_logs_action (action, created_at),
  KEY idx_audit_logs_actor (actor_id, created_at),
  KEY idx_audit_logs_target (target_type, target_id)
) ENGINE=InnoDB;
