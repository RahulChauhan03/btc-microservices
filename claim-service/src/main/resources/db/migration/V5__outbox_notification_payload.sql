-- Phase 7: the claim outbox also delivers notification events to notification-service. Their content travels
-- in "payload" (JSON: audience, recipient, type, title, message, link; no secrets or tokens).
-- Additive and idempotent: adds one nullable column; existing rows are untouched.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_outbox' AND COLUMN_NAME = 'payload') = 0,
              'ALTER TABLE claim_outbox ADD COLUMN payload varchar(2000) DEFAULT NULL', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
