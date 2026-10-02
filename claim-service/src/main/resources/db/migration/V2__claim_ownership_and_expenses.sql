-- Phase 3: claim ownership, review audit fields and the claim-to-expense link table.
-- Idempotent: every change is applied only if it is missing, so this is safe both on databases where
-- Hibernate already added these objects (Phase 3 ran with ddl-auto=update) and on ones where it did not.
-- Additive only: no column, row or constraint is altered or removed.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'owner_id') = 0,
              'ALTER TABLE claims ADD COLUMN owner_id bigint DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'trip_id') = 0,
              'ALTER TABLE claims ADD COLUMN trip_id bigint DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'reviewed_by') = 0,
              'ALTER TABLE claims ADD COLUMN reviewed_by bigint DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'reviewed_at') = 0,
              'ALTER TABLE claims ADD COLUMN reviewed_at datetime(6) DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND INDEX_NAME = 'idx_claims_owner_id') = 0,
              'CREATE INDEX idx_claims_owner_id ON claims (owner_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS claim_expenses (
  claim_id bigint NOT NULL,
  expense_id bigint NOT NULL,
  PRIMARY KEY (claim_id, expense_id),
  KEY idx_claim_expenses_expense_id (expense_id),
  CONSTRAINT FKk3auypaae5uukofg1b94my362 FOREIGN KEY (claim_id) REFERENCES claims (id)
) ENGINE=InnoDB;
