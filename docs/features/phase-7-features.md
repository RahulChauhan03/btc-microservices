# Phase 7: dashboards, trip details, notifications, reports, directory, travel policy, reimbursements, audit and profile

Every rule below is enforced by the backend services. Angular route guards and hidden buttons are conveniences
only.

## Pages and roles

| Page (route) | Employee | Admin | Backend |
|---|---|---|---|
| Dashboard `/dashboard` | Own upcoming trips, spending (12 months), claims by status, recent activity, quick actions | Company-wide totals, pending approvals, monthly and category spending, team stats | trip, expense, claim and user summaries |
| Trip details `/trips/view/:id` | Own trips | All trips (with owner name) | trip-service, plus expense and claim lists filtered by `tripId` |
| Notifications `/notifications` (bell in the top bar) | Own | Own, plus notices to all administrators | **notification-service** (new) |
| Reports `/reports` | — | Monthly and category spending, spending per trip, claim statuses, date range, CSV export | expense- and claim-service report endpoints |
| Employee directory `/employees`, `/employees/:id` | — | Search, role filter, profile with trips, expenses and claims | user-service; `ownerId` filters (admin only) |
| Travel policy `/policy` | Read | Create and edit | expense-service |
| Reimbursements `/reimbursements` | Own (read) | All; record status changes | claim-service |
| Audit log `/audit` | — | Search per service | user-, claim- and expense-service |
| Profile & settings `/profile` (account menu) | Own | Own | user-service `/users/me` |

The existing screens (login, forgot and reset password, trips, expenses, claims, users) are unchanged. A
"View" action was added to the trips table, and the new pages reuse the existing design system.

## New and changed APIs

All endpoints are behind the gateway and need a JWT. "Admin" endpoints are rejected with 403 at the security
filter, before the request body is read; the service layer checks again. Lists are paged (`page`, `size`, `sort`)
and return the total in `X-Total-Count`.

| Service | Endpoint | Access |
|---|---|---|
| trip | `GET /trips?ownerId&status&upcoming&ids` | Filters; `ownerId` is admin-only (403 for anyone else); `ids` (at most 100) is a batch lookup within the caller's scope |
| trip | `GET /trips/summary?ownerId` | Counts by status, plus upcoming |
| expense | `GET /expenses?tripId&ownerId&category&from&to` | Filters |
| expense | `GET /expenses/summary?ownerId&tripId&from&to` | Totals by category and month, aggregated in SQL; range at most 2 years |
| expense | `GET /expenses/reports/summary`, `/by-trip`, `/export.csv` | Admin. The CSV has at most 50,000 rows, and formula cells are neutralised |
| expense | `GET /expenses/policies`, `GET /expenses/policies/applicable?date` | Everyone |
| expense | `POST /expenses/policies`, `PUT /expenses/policies/{id}` | Admin |
| expense | `GET /expenses/audit-logs` | Admin |
| claim | `GET /claims?ownerId&tripId&status=A,B` | Filters |
| claim | `GET /claims/summary?ownerId` | Counts and amounts by status |
| claim | `GET /claims/reports/status?from&to` | Admin |
| claim | `GET /claims/reimbursements?ownerId&status` | Own (employee) or all (admin) |
| claim | `GET /claims/{id}/reimbursement` | Owner or admin |
| claim | `PUT /claims/{id}/reimbursement` | Admin, never on their own claim |
| claim | `GET /claims/audit-logs` | Admin |
| user | `GET /users?q&role`, `GET /users/stats`, `GET /users/audit-logs` | Admin |
| user | `GET/PUT /users/me`, `POST /users/me/password` | Self |
| notification | `GET /notifications?unreadOnly`, `GET /notifications/unread-count` | Own only |
| notification | `POST /notifications/{id}/read`, `POST /notifications/read-all` | Own only |
| notification | `POST /notifications/internal/events` | SERVICE token only; the gateway never routes `/*/internal/**` |

### Domain rules

**Travel policy**
* At most one policy applies to a date; overlapping periods are refused with 409.
* A policy can limit the amount of a single expense per category, and the total spending on a trip.
* A new or changed expense that breaks the applicable policy is rejected with **422** and a clear message.
  Amounts are never changed, and existing expenses are not re-validated.
* The trip check write-locks the trip's expense rows. Two simultaneous expenses therefore cannot jointly exceed
  the limit; this is verified on MySQL.
* Expenses carry no currency (single-currency app, `decimal(12,2)`). A policy's currency must equal
  `btc.expenses.currency` (default USD).

**Reimbursements** (payment records only; no money is moved)
* The lifecycle is `PENDING → PROCESSING | PAID`, `PROCESSING → PAID | FAILED`, `FAILED → PROCESSING`.
  `PAID` is final.
* Each claim has one reimbursement, enforced by a unique key, and it is created in the same transaction as the
  approval.
* `PAID` requires a payment date that is not in the future and a payment reference unique across all
  reimbursements.
* Only admins may change a reimbursement, and never for their own claim. Every change is audited and the owner
  is notified.

**Notifications**
* claim-service records them in its existing outbox, in the same transaction as the change, so delivery is
  retried and at-least-once.
* notification-service stores each event once (unique `event_id`).
* Events:

  | Event | Recipient |
  |---|---|
  | Claim submitted | All admins |
  | Claim approved | Owner |
  | Claim rejected | Owner; the message says the claim's expenses are unlocked again |
  | Reimbursement updated | Owner |

* Trips and expenses change only by their owners, so no further trip or expense notifications are generated.

**Audit**
* Each service writes its own `audit_logs` table in the same transaction as the change. Records are read-only:
  there is no write API.

  | Service | Actions recorded |
  |---|---|
  | user-service | `USER_CREATED`, `USER_ROLE_CHANGED`, `USER_DELETED`, `USER_PASSWORD_CHANGED`, `USER_PASSWORD_RESET` |
  | claim-service | `CLAIM_APPROVED`, `CLAIM_REJECTED`, `REIMBURSEMENT_STATUS_CHANGED` |
  | expense-service | `TRAVEL_POLICY_CREATED`, `TRAVEL_POLICY_UPDATED` |

* Summaries contain ids, roles, statuses and amounts only: never emails, passwords, tokens or credentials.
* Other actions are **not** audited.

**Profile**
* Users can change their name and phone.
* Email (the sign-in identity) and role are read-only.
* A password change requires the current password, is rate-limited (10 attempts per 15 minutes) and revokes
  open reset links.
* `PUT /users/{id}` now **refuses** a change to your *own* password; the reason is to require proof of the
  current password. Admins can still set other users' passwords, and that is audited.

## Database migrations (Flyway, additive)

| Database | New migrations | What they do |
|---|---|---|
| `user_service_db` | `V3__audit_logs` | New table `audit_logs` |
| `trip_service_db` | none | — |
| `expense_service_db` | `V4__travel_policies`, `V5__audit_logs` | New tables `travel_policies`, `travel_policy_limits`, `audit_logs` |
| `claim_service_db` | `V5__outbox_notification_payload`, `V6__reimbursements_and_audit` | Adds nullable `claim_outbox.payload`. Adds tables `claim_reimbursements` and `audit_logs`, and **inserts** one `PENDING` reimbursement for every claim that is already `APPROVED`. Existing rows are not modified. |
| `notification_service_db` (new database) | `V1__notifications` | New tables `notifications`, `notification_reads` |

**Verified on throwaway MySQL 9.6.** The databases were first brought to user V2, trip V2, expense V3 and claim
V4 (the state of your databases), with a claim approved before the upgrade. The new code then applied every
pending migration with 0 failures and passed Hibernate validation, and the earlier approved claim received its
`PENDING` reimbursement.

### Applying them to your databases (after your approval)

1. **Stop the services**, or disable DevTools restart with `-Dspring.devtools.restart.enabled=false`. A rebuild
   while services run restarts them on the new code, which applies the migrations immediately.
2. **Read-only check of the current history** (expected: user 1,2; trip 1,2; expense 1,2,3; claim 1,2,3,4):
   ```sql
   SELECT 'user', GROUP_CONCAT(version ORDER BY installed_rank) FROM user_service_db.flyway_schema_history UNION ALL
   SELECT 'trip', GROUP_CONCAT(version ORDER BY installed_rank) FROM trip_service_db.flyway_schema_history UNION ALL
   SELECT 'expense', GROUP_CONCAT(version ORDER BY installed_rank) FROM expense_service_db.flyway_schema_history UNION ALL
   SELECT 'claim', GROUP_CONCAT(version ORDER BY installed_rank) FROM claim_service_db.flyway_schema_history;
   ```
   If a database has no `flyway_schema_history`, follow `docs/migrations/phase-4-flyway.md` first. Do **not**
   set `BTC_FLYWAY_BASELINE_EXISTING` on databases that already have history.
3. **Back up:**
   `mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF -u <user> -p --databases user_service_db trip_service_db expense_service_db claim_service_db > before-phase7.sql`
4. **Create a run configuration for notification-service** (port 8086) with the same environment variables as
   the other services: `MYSQL_USER`, `MYSQL_PASSWORD`, `BTC_JWT_SECRET` and, if not default, `MYSQL_HOST`,
   `MYSQL_PORT`, `CONFIG_SERVER_URL`. Its database is created on first start; its URL is in
   `config-repo/notification-service.properties`.
5. **Start** config-server and service-registry, then user, trip, expense, notification and claim service, then
   the gateway. Each service applies only its pending migrations.
6. **Verify:**
   ```sql
   SELECT version, success FROM claim_service_db.flyway_schema_history ORDER BY installed_rank;     -- ... 5 1, 6 1
   SELECT version, success FROM expense_service_db.flyway_schema_history ORDER BY installed_rank;   -- ... 4 1, 5 1
   SELECT version, success FROM user_service_db.flyway_schema_history ORDER BY installed_rank;      -- ... 3 1
   SELECT COUNT(*) FROM claim_service_db.claims c WHERE c.status = 'APPROVED'
     AND NOT EXISTS (SELECT 1 FROM claim_service_db.claim_reimbursements r WHERE r.claim_id = c.id);  -- 0
   ```

**Rollback:** restore the step 3 backup and deploy the previous build. The manual alternative discards the new
data:
```sql
DROP TABLE claim_service_db.audit_logs, claim_service_db.claim_reimbursements;
ALTER TABLE claim_service_db.claim_outbox DROP COLUMN payload;
DROP TABLE expense_service_db.audit_logs, expense_service_db.travel_policy_limits, expense_service_db.travel_policies;
DROP TABLE user_service_db.audit_logs;
DROP DATABASE notification_service_db;
DELETE FROM claim_service_db.flyway_schema_history WHERE version IN ('5', '6');
DELETE FROM expense_service_db.flyway_schema_history WHERE version IN ('4', '5');
DELETE FROM user_service_db.flyway_schema_history WHERE version = '3';
```

## Known limitations

* **Lists are paged on the server.** Existing management tables still load their first 100 rows, as before. The
  new pages page through results.
* **Audit search is per service.** There is no single cross-service timeline; each source is searched
  separately.
* **No currency per expense.** Policies are therefore single-currency.
* **Notification delivery is at-least-once with retries.** It is not real-time: there is no push, and the bell
  refreshes every 60 seconds and on page load.
* **No per-user preferences** were added. Nothing would use them yet.
* **Existing JWT sessions stay valid until they expire** (8 hours) after a password change or reset.

## Troubleshooting: a page shows "Invalid value for 'id'", or 404 or 503 for a new feature

These symptoms mean the running service is older than the frontend, or isn't running at all.

| Symptom | Cause |
|---|---|
| `GET /claims/summary`, `/claims/reimbursements` or `/claims/audit-logs` answers **400 "Invalid value for 'id'"** | An old claim-service build: the path falls through to `GET /claims/{id}`. |
| A new endpoint answers **404** | Same: an old build lacks the endpoint. |
| A request answers **503** from the gateway | The service is not running or not registered in Eureka, e.g. notification-service. |

**Checks:**
* `ls claim-service/target/classes/db/migration` should list `V5__…` and `V6__…`.
* `find claim-service/target/classes -name ReimbursementController.class` should find the class.
* `curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps` should list `NOTIFICATION-SERVICE`.

**IntelliJ:**
* Every Spring Boot run configuration needs **Before launch → Build**. Without it, a restart reuses stale
  `target/classes`.
* notification-service needs its own run configuration: port 8086, with the same environment variables as the
  other services.

After restarting a service, the gateway can answer 503 for up to about a minute while Eureka's registry cache
refreshes. The dashboard and reports keep their other sections and name the unavailable service.
