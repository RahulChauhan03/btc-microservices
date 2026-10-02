-- Phase 3: expense ownership and optional trip link.
-- Idempotent: every change is applied only if it is missing, so this is safe both on databases where
-- Hibernate already added these objects (Phase 3 ran with ddl-auto=update) and on ones where it did not.
-- Additive only: no column, row or constraint is altered or removed.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expenses' AND COLUMN_NAME = 'owner_id') = 0,
              'ALTER TABLE expenses ADD COLUMN owner_id bigint DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expenses' AND COLUMN_NAME = 'trip_id') = 0,
              'ALTER TABLE expenses ADD COLUMN trip_id bigint DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expenses' AND INDEX_NAME = 'idx_expenses_owner_id') = 0,
              'CREATE INDEX idx_expenses_owner_id ON expenses (owner_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expenses' AND INDEX_NAME = 'idx_expenses_trip_id') = 0,
              'CREATE INDEX idx_expenses_trip_id ON expenses (trip_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

