-- SUPERSEDED by Flyway (Phase 4): the same changes now ship as the idempotent V2 scripts in each service's
-- src/main/resources/db/migration and run automatically. Do NOT run the DDL below by hand; follow
-- docs/migrations/phase-4-flyway.md instead. The reconciliation queries (step 3) remain valid.
--
-- BTC Flow Phase 3: ownership and claim workflow schema changes (MySQL).
--
-- The project has no migration framework yet; with spring.jpa.hibernate.ddl-auto=update each service
-- applies the equivalent of the DDL below automatically on its next start. All changes are ADDITIVE:
-- new nullable columns, one new table and indexes. No existing column, row or constraint is changed or removed.
--
-- Recommended order:
--   1. Back up the four databases, e.g.  mysqldump --databases user_service_db trip_service_db \
--        expense_service_db claim_service_db > btc-before-phase3.sql
--   2. Either run the DDL below yourself, or start the services and let Hibernate add it.
--   3. Run the reconciliation queries (step 3) and assign owners explicitly.
--
-- users.role needs no schema change: existing values are already ADMIN/EMPLOYEE (null or unknown values
-- are treated as EMPLOYEE, the least privileged role).

-- 2a. trip_service_db
ALTER TABLE trip_service_db.trips ADD COLUMN owner_id BIGINT NULL;
CREATE INDEX idx_trips_owner_id ON trip_service_db.trips (owner_id);

-- 2b. expense_service_db
ALTER TABLE expense_service_db.expenses ADD COLUMN owner_id BIGINT NULL, ADD COLUMN trip_id BIGINT NULL;
CREATE INDEX idx_expenses_owner_id ON expense_service_db.expenses (owner_id);
CREATE INDEX idx_expenses_trip_id ON expense_service_db.expenses (trip_id);

-- 2c. claim_service_db
ALTER TABLE claim_service_db.claims
    ADD COLUMN owner_id BIGINT NULL,
    ADD COLUMN trip_id BIGINT NULL,
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at DATETIME(6) NULL;
CREATE INDEX idx_claims_owner_id ON claim_service_db.claims (owner_id);
CREATE TABLE claim_service_db.claim_expenses (
    claim_id   BIGINT NOT NULL,
    expense_id BIGINT NOT NULL,
    CONSTRAINT uk_claim_expenses UNIQUE (claim_id, expense_id),
    CONSTRAINT fk_claim_expenses_claim FOREIGN KEY (claim_id) REFERENCES claim_service_db.claims (id)
);
CREATE INDEX idx_claim_expenses_expense_id ON claim_service_db.claim_expenses (expense_id);

-- 3. Reconciliation. Records created before Phase 3 have owner_id = NULL: they are visible (read-only)
--    to administrators only and cannot be modified, approved or rejected by anyone until reconciled.
--    Ownership cannot be inferred from the existing data, so it must be assigned deliberately.
--
-- 3a. Find what needs an owner:
--   SELECT id, trip_code, destination, start_date FROM trip_service_db.trips WHERE owner_id IS NULL;
--   SELECT id, title, amount, expense_date FROM expense_service_db.expenses WHERE owner_id IS NULL;
--   SELECT id, claim_number, title, claim_amount, status FROM claim_service_db.claims WHERE owner_id IS NULL;
--   SELECT id, name, email, role FROM user_service_db.users;   -- the ids to assign
--
-- 3b. Assign each record to its real owner by explicit id (never in bulk to a default user), e.g.:
--   UPDATE trip_service_db.trips       SET owner_id = <user id> WHERE id IN (<trip ids>)    AND owner_id IS NULL;
--   UPDATE expense_service_db.expenses SET owner_id = <user id> WHERE id IN (<expense ids>) AND owner_id IS NULL;
--   UPDATE claim_service_db.claims     SET owner_id = <user id> WHERE id IN (<claim ids>)   AND owner_id IS NULL;
--
-- 3c. Legacy claims have no linked expenses and a client-entered amount. Review is blocked for them by design.
--     Decide per claim: have the owner delete it and resubmit from expenses, or link expenses explicitly:
--   INSERT INTO claim_service_db.claim_expenses (claim_id, expense_id) VALUES (<claim id>, <expense id>);
--   UPDATE claim_service_db.claims c SET claim_amount = (<sum of the linked expense amounts>) WHERE c.id = <claim id>;
--
-- Rollback (only if no Phase 3 data has been written): drop the added columns/table/indexes above.
