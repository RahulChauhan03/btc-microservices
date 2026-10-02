-- Phase 3: trip ownership.
-- Idempotent: every change is applied only if it is missing, so this is safe both on databases where
-- Hibernate already added these objects (Phase 3 ran with ddl-auto=update) and on ones where it did not.
-- Additive only: no column, row or constraint is altered or removed.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trips' AND COLUMN_NAME = 'owner_id') = 0,
              'ALTER TABLE trips ADD COLUMN owner_id bigint DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trips' AND INDEX_NAME = 'idx_trips_owner_id') = 0,
              'CREATE INDEX idx_trips_owner_id ON trips (owner_id)', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

