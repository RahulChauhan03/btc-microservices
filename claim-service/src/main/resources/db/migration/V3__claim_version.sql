-- Phase 4: optimistic-locking version, so two concurrent reviews cannot both succeed.
-- Idempotent: every change is applied only if it is missing, so this is safe both on databases where
-- Hibernate already added these objects (Phase 3 ran with ddl-auto=update) and on ones where it did not.
-- Additive only: no column, row or constraint is altered or removed.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claims' AND COLUMN_NAME = 'version') = 0,
              'ALTER TABLE claims ADD COLUMN version bigint NOT NULL DEFAULT 0', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

