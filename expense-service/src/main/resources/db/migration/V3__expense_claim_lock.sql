-- Phase 4: claim lock (the claim currently covering the expense) and optimistic-locking version.
-- Existing rows start unlocked with version 0. For locks of claims created under Phase 3, see the
-- backfill step in docs/migrations/phase-4-flyway.md (it spans two databases, so it is not automatic).
-- Idempotent: every change is applied only if it is missing, so this is safe both on databases where
-- Hibernate already added these objects (Phase 3 ran with ddl-auto=update) and on ones where it did not.
-- Additive only: no column, row or constraint is altered or removed.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expenses' AND COLUMN_NAME = 'claim_id') = 0,
              'ALTER TABLE expenses ADD COLUMN claim_id bigint DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expenses' AND COLUMN_NAME = 'version') = 0,
              'ALTER TABLE expenses ADD COLUMN version bigint NOT NULL DEFAULT 0', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expenses' AND INDEX_NAME = 'idx_expenses_claim_id') = 0,
              'CREATE INDEX idx_expenses_claim_id ON expenses (claim_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

