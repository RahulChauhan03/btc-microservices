# Phase 4: Flyway migrations and data-integrity runbook

Flyway now owns the schema of the four database services; Hibernate only validates it
(`spring.jpa.hibernate.ddl-auto=validate`, shared in `config-server/config-repo/application.properties`).

| Service | Migrations (`src/main/resources/db/migration`) |
|---|---|
| user-service | `V1__baseline` |
| trip-service | `V1__baseline`, `V2__trip_ownership` |
| expense-service | `V1__baseline`, `V2__expense_ownership`, `V3__expense_claim_lock` |
| claim-service | `V1__baseline`, `V2__claim_ownership_and_expenses`, `V3__claim_version`, `V4__claim_outbox` (Phase 5, see `docs/operations/phase-5-claim-outbox.md`) |

* `V1` is the schema Hibernate created before Flyway, copied from a MySQL replica. It runs **only on an empty
  database**. Existing databases are *baselined* at version 1, so V1 is recorded but never executed there.
* `V2`/`V3` are **additive and idempotent**: each column, index or table is created only if missing. They are safe
  whether or not Hibernate already added the Phase 3 columns (`ddl-auto=update`).
* Nothing is dropped, renamed or rewritten. `spring.flyway.clean-disabled=true` is always set.

## Safety model

| Database state at startup | What happens |
|---|---|
| Empty (new install) | V1..V3 run automatically. |
| Has `flyway_schema_history` | Pending scripts run (none, once migrated). |
| Has tables but **no** `flyway_schema_history` (every database created before Phase 4) | **Startup is refused**, nothing is changed, until `BTC_FLYWAY_BASELINE_EXISTING=true` is set for one start. |

## 0. Do this first: development restarts may already have touched your databases

Your IntelliJ run configurations load each service from `<module>/target/classes` with
`spring-boot-devtools` on the classpath. Any build that rewrites `target/classes` (including `mvn test` /
`mvn package`) makes DevTools **restart the running service on the new code**. During Phases 3 and 4 this
happened, and at the time of writing user-service and trip-service were running Phase 4 code while claim- and
expense-service had failed their restart. So:

* while the shared config still had `ddl-auto=update` (Phase 3), Hibernate may have added the Phase 3 columns;
* with the Phase 4 config, a successful start of user-/trip-service means Flyway ran against the database it
  is connected to.

**Before anything else:** stop the IntelliJ services, then run these read-only checks (no changes):

```sql
-- Which databases already have Flyway history, and what was applied?
SELECT table_schema FROM information_schema.tables WHERE table_name = 'flyway_schema_history';
SELECT installed_rank, version, type, script, success, installed_on FROM user_service_db.flyway_schema_history;
SELECT installed_rank, version, type, script, success, installed_on FROM trip_service_db.flyway_schema_history;
SELECT installed_rank, version, type, script, success, installed_on FROM expense_service_db.flyway_schema_history;
SELECT installed_rank, version, type, script, success, installed_on FROM claim_service_db.flyway_schema_history;

-- Which Phase 3/4 objects exist? (empty result = not applied)
SELECT table_schema, table_name, column_name FROM information_schema.columns
 WHERE table_schema IN ('trip_service_db','expense_service_db','claim_service_db')
   AND column_name IN ('owner_id','trip_id','claim_id','version','reviewed_by','reviewed_at');
SELECT table_schema, table_name FROM information_schema.tables
 WHERE table_schema = 'claim_service_db' AND table_name = 'claim_expenses';

-- Row counts, to compare with your expectations / backups
SELECT 'users', COUNT(*) FROM user_service_db.users UNION ALL
SELECT 'trips', COUNT(*) FROM trip_service_db.trips UNION ALL
SELECT 'expenses', COUNT(*) FROM expense_service_db.expenses UNION ALL
SELECT 'claims', COUNT(*) FROM claim_service_db.claims;
```

To stop builds from restarting running services, either stop them before building, or set
`spring.devtools.restart.enabled=false` in the IntelliJ run configurations (VM option
`-Dspring.devtools.restart.enabled=false`).

## 1. Back up (required before migrating)

```bash
mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF \
  -u "$MYSQL_USER" -p --databases user_service_db trip_service_db expense_service_db claim_service_db \
  > btc-before-phase4-$(date +%Y%m%d-%H%M).sql
```

Check the file is non-empty and contains `CREATE TABLE` for `users`, `trips`, `expenses`, `claims`.

## 2. Migrate existing databases (one time)

With the services stopped and the backup taken, start **each** of user-, trip-, expense- and claim-service once with

```
BTC_FLYWAY_BASELINE_EXISTING=true
```

(IntelliJ: add it to the run configuration's environment variables; terminal: `export` it before starting).
Each service logs `Successfully baselined schema with version: 1` and then applies its V2/V3.
**Then remove the variable** and restart normally; from now on Flyway runs only new scripts.

If step 0 showed a database already has `flyway_schema_history` with successful rows, do **not** set the
variable for that service; just start it and check its history in step 3.

## 3. Verify

```sql
-- every row success = 1; expected versions per service as in the table at the top
SELECT version, type, success FROM expense_service_db.flyway_schema_history ORDER BY installed_rank;
-- existing data untouched: compare with the counts from step 0
```

The services start only if Hibernate validation of the migrated schema succeeds.

## 4. Lock backfill for claims created under Phase 3 (only if any exist)

Phase 4 records which claim locks an expense in `expenses.claim_id`. Claims created by the Phase 3 code (if any
were created before this upgrade) have no lock yet. Because the data spans two databases on the same server,
this is a manual, reviewed step:

```sql
-- Preview: expenses that belong to a claim still awaiting review or approved
SELECT e.id AS expense_id, ce.claim_id, c.status
  FROM claim_service_db.claim_expenses ce
  JOIN claim_service_db.claims c ON c.id = ce.claim_id AND c.status <> 'REJECTED'
  JOIN expense_service_db.expenses e ON e.id = ce.expense_id;

-- Apply (only rows that are not locked yet)
UPDATE expense_service_db.expenses e
  JOIN claim_service_db.claim_expenses ce ON ce.expense_id = e.id
  JOIN claim_service_db.claims c ON c.id = ce.claim_id AND c.status <> 'REJECTED'
   SET e.claim_id = ce.claim_id, e.version = e.version + 1
 WHERE e.claim_id IS NULL;
```

## 5. Releasing a stuck expense lock

Since Phase 5, releases go through the claim outbox and are retried automatically; recover a `FAILED` event as
described in `docs/operations/phase-5-claim-outbox.md` rather than editing locks directly. Use the SQL below
only when no outbox event exists for the claim (see "Reconciling locks" there), after confirming claim `N` is
rejected or deleted:

```sql
SELECT id, status FROM claim_service_db.claims WHERE id = <N>;            -- must be REJECTED or no row
UPDATE expense_service_db.expenses SET claim_id = NULL, version = version + 1 WHERE claim_id = <N>;
```

## 6. Rollback

Preferred: restore the step 1 backup (`mysql -u ... -p < btc-before-phase4-....sql`) and run the previous
release. The scripts only add objects, so a manual rollback is possible too, but it discards the ownership, claim
link and lock data written since:

```sql
-- claim_service_db
DROP TABLE IF EXISTS claim_expenses;
ALTER TABLE claims DROP COLUMN version, DROP COLUMN owner_id, DROP COLUMN trip_id,
                   DROP COLUMN reviewed_by, DROP COLUMN reviewed_at;
-- expense_service_db
ALTER TABLE expenses DROP COLUMN claim_id, DROP COLUMN version, DROP COLUMN owner_id, DROP COLUMN trip_id;
-- trip_service_db
ALTER TABLE trips DROP COLUMN owner_id;
-- every service database
DROP TABLE flyway_schema_history;
```

Then set `ddl-auto` back in the shared config and redeploy the previous code.

## 7. Testing migrations without your data

Ordinary tests use in-memory H2. The `*MySqlTests` classes run Flyway + Hibernate validation and the concurrency
tests on real MySQL when these are set (each uses its own `btc_it_*` database; never point them at real data):

```bash
export BTC_IT_MYSQL_URL=jdbc:mysql://127.0.0.1:3306 BTC_IT_MYSQL_USER=... BTC_IT_MYSQL_PASSWORD=...
./mvnw test
```

A disposable server works well and needs no Docker:

```bash
mysqld --no-defaults --initialize-insecure --datadir=/tmp/btc-it-data
mysqld --no-defaults --datadir=/tmp/btc-it-data --port=13306 --bind-address=127.0.0.1 \
       --socket=/tmp/btc-it.sock --mysqlx=OFF &
```
