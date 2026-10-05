-- Phase 7: reimbursement tracking for approved claims (payment records only; no money is moved by BTC Flow)
-- and the claim-service audit log. Additive: two new tables, plus one INSERT that creates a PENDING
-- reimbursement for every claim that is already APPROVED (existing rows are not modified).

CREATE TABLE IF NOT EXISTS claim_reimbursements (
  id bigint NOT NULL AUTO_INCREMENT,
  claim_id bigint NOT NULL,
  owner_id bigint DEFAULT NULL,
  status varchar(20) NOT NULL,
  amount decimal(12,2) NOT NULL,
  payment_date date DEFAULT NULL,
  payment_reference varchar(100) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  updated_by bigint DEFAULT NULL,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_reimbursements_claim (claim_id),
  UNIQUE KEY uk_claim_reimbursements_reference (payment_reference),
  KEY idx_claim_reimbursements_owner (owner_id),
  KEY idx_claim_reimbursements_status (status),
  CONSTRAINT fk_claim_reimbursements_claim FOREIGN KEY (claim_id) REFERENCES claims (id)
) ENGINE=InnoDB;

INSERT INTO claim_reimbursements (claim_id, owner_id, status, amount, created_at, updated_at, updated_by, version)
SELECT c.id, c.owner_id, 'PENDING', c.claim_amount, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), c.reviewed_by, 0
  FROM claims c
 WHERE c.status = 'APPROVED'
   AND NOT EXISTS (SELECT 1 FROM claim_reimbursements r WHERE r.claim_id = c.id);

-- Written by claim-service in the same transaction as the audited change; never updated or deleted by the app.
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
