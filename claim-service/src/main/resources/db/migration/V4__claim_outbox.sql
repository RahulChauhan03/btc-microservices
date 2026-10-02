-- Phase 5: transactional outbox. A reject/delete writes its expense-lock release request here in the same
-- transaction as the claim change; a background processor delivers it to expense-service with retries.
-- Additive only: creates one new table and touches no existing table or row.

CREATE TABLE IF NOT EXISTS claim_outbox (
  id bigint NOT NULL AUTO_INCREMENT,
  event_id varchar(36) NOT NULL,
  event_type varchar(40) NOT NULL,
  claim_id bigint NOT NULL,
  expense_ids varchar(2000) NOT NULL,
  status varchar(20) NOT NULL,
  attempts int NOT NULL,
  next_attempt_at datetime(6) NOT NULL,
  locked_by varchar(64) DEFAULT NULL,
  locked_until datetime(6) DEFAULT NULL,
  last_error varchar(1000) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  completed_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_outbox_event_id (event_id),
  KEY idx_claim_outbox_status_next_attempt (status, next_attempt_at),
  KEY idx_claim_outbox_claim_id (claim_id)
) ENGINE=InnoDB;
