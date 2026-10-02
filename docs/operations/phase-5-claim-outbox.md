# Phase 5: claim outbox (reliable expense-lock release)

When a claim is **rejected** or **deleted**, its expenses must be unlocked in expense-service. claim-service
writes that request to the `claim_outbox` table **in the same transaction** as the claim change, and a
background processor delivers it. A creation that locked expenses and then rolled back records a release too,
in its own transaction, delayed 30 s so it cannot overtake a lock call that is still finishing.

* If expense-service is down, the reject or delete still succeeds. The event waits and is retried.
* An event is marked `COMPLETED` only after expense-service has confirmed the release (HTTP 2xx).
* Delivery happens at least once. The release (`DELETE /expenses/internal/claims/{id}/locks`, SERVICE token)
  is idempotent and only clears expenses whose `claim_id` is that claim, so repeats are harmless and can never
  unlock another claim's expenses.
* Before sending, the processor checks the claim. A release is never sent while the claim exists and is not
  `REJECTED`; such an event goes straight to `FAILED`.
* Approval keeps expenses locked, exactly as in Phase 4. Ownership and authorization rules are unchanged.

## Lifecycle

| Status | Meaning |
|---|---|
| `PENDING` | Waiting for `next_attempt_at`. `attempts > 0` means it is being retried. |
| `IN_PROGRESS` | Taken by a worker (`locked_by`) until `locked_until`. If that worker dies, the lease expires and another worker takes the event. |
| `COMPLETED` | Released. Kept for audit. |
| `FAILED` | No more automatic attempts, for one of these reasons: the attempt limit was reached; expense-service refused permanently (any HTTP 4xx except 401, 408 and 429); or the claim is still active. **The expenses stay locked** until someone recovers the event. |

Each worker takes an event with a conditional `UPDATE`, so two threads or instances never process the same
event at the same time.

Delivery is attempted in two ways:

* **Right after commit**, on a background thread.
* **Every poll interval**, which also picks up events left over from a restart.

Retry delays are 5 s, 10 s, 20 s and so on, capped at 10 min, for 10 attempts (about 40 min in total).

## Settings (`btc.outbox.*`, optional; defaults shown)

```
btc.outbox.poll-interval=PT5S
btc.outbox.batch-size=20
btc.outbox.max-attempts=10
btc.outbox.initial-backoff=PT5S
btc.outbox.max-backoff=PT10M
btc.outbox.lease=PT2M
btc.outbox.dispatch-after-commit=true
btc.outbox.scheduling-enabled=true
```

## Monitoring

**Logs** (logger `com.btc.claimservice.outbox`) contain event ids, claim ids, expense ids, attempt numbers and
exception messages. They never contain tokens or request headers.

| Level | Message |
|---|---|
| `INFO` | `... recorded` and `... delivered on attempt N` |
| `WARN` | `... attempt N/10 failed: ...; next attempt at ...` |
| `ERROR` | `... FAILED after N attempt(s) ...` |
| `WARN` | `N claim outbox event(s) are FAILED` (logged whenever that count changes) |

**Metrics** (Micrometer; claim-service now includes Actuator):

| Metric | Meaning |
|---|---|
| `btc.claim.outbox.events{state=pending\|retrying\|in_progress\|failed}` | Current counts, refreshed every poll. `retrying` is the part of `pending` that has failed at least once. |
| `btc.claim.outbox.deliveries{outcome=completed\|retry_scheduled\|failed}` | Delivery attempts since startup. |

Only `health` and `info` are exposed over HTTP (shared config). To read metrics, add `metrics` to
`management.endpoints.web.exposure.include` for claim-service only. Every claim-service endpoint requires a valid
JWT, and the gateway does not route `/actuator/**` of the services.

**Alert on:**

* `state=failed > 0`.
* `state=retrying` staying above 0 for a long time.

**Read-only inspection:**

```sql
-- Overview
SELECT status, COUNT(*), MIN(created_at) FROM claim_service_db.claim_outbox GROUP BY status;

-- What needs attention
SELECT event_id, event_type, claim_id, expense_ids, status, attempts, next_attempt_at, locked_by, locked_until,
       last_error, created_at
  FROM claim_service_db.claim_outbox
 WHERE status IN ('FAILED', 'IN_PROGRESS') OR (status = 'PENDING' AND attempts > 0)
 ORDER BY id;
```

## Recovering a FAILED event

1. **Read `last_error`** and fix the cause. Typical causes:
   - expense-service is down;
   - the JWT secret or issuer differs between services (HTTP 401 or 403);
   - a routing or Eureka problem.

   If the error is `Claim N is SUBMITTED/APPROVED; its expenses must stay locked`, **do not retry**. Find out
   why the claim is active again; the expenses are correctly locked.
2. **Confirm the claim** is deleted or rejected:
   ```sql
   SELECT id, status FROM claim_service_db.claims WHERE id = <claim_id>;   -- no row, or REJECTED
   ```
3. **Re-queue the event** with a fresh attempt budget. Only `FAILED` rows are affected; the processor picks it up
   on its next poll.
   ```sql
   UPDATE claim_service_db.claim_outbox
      SET status = 'PENDING', attempts = 0, next_attempt_at = '2000-01-01 00:00:00',  -- any past time = due now
          last_error = NULL, locked_by = NULL, locked_until = NULL
    WHERE event_id = '<event_id>' AND status = 'FAILED';
   ```
4. **Check that it reaches `COMPLETED`.** The expenses' `claim_id` should now be `NULL`:
   ```sql
   SELECT id, claim_id FROM expense_service_db.expenses WHERE id IN (<expense_ids>);
   ```

Timestamps are stored in UTC (the config-repo JDBC URLs set `serverTimezone=UTC`; the service converts from its
own zone, see `OutboxConfig.now`). Never set an event to `COMPLETED` by hand; that would hide an unreleased lock.
Re-queuing an event that already completed is harmless, because the release is idempotent.

An `IN_PROGRESS` event whose `locked_until` is in the past is picked up automatically, so no action is needed.

## Reconciling locks

These cases need a manual check:

* **A claim update failed and could not restore its previous locks.** claim-service logs `Restoring expense
  locks of claim N failed`.
* **The outbox itself could not be written.** claim-service logs `Could not record the expense-lock release
  ...`.

Read-only check (both databases are on the same server):

```sql
-- Locked expenses whose claim is gone or rejected: these should be released (or have a pending event)
SELECT e.id, e.claim_id, c.status
  FROM expense_service_db.expenses e
  LEFT JOIN claim_service_db.claims c ON c.id = e.claim_id
 WHERE e.claim_id IS NOT NULL AND (c.id IS NULL OR c.status = 'REJECTED');

-- Expenses of active claims that are not locked by them
SELECT ce.claim_id, ce.expense_id, e.claim_id AS locked_by
  FROM claim_service_db.claim_expenses ce
  JOIN claim_service_db.claims c ON c.id = ce.claim_id AND c.status <> 'REJECTED'
  JOIN expense_service_db.expenses e ON e.id = ce.expense_id
 WHERE e.claim_id IS NULL OR e.claim_id <> ce.claim_id;
```

* **Rows from the first query:** if no `PENDING` event exists for that claim, release the lock with the SQL in
  `docs/migrations/phase-4-flyway.md` §5.
* **Rows from the second query:** re-save the claim (edit it with the same expenses) or contact its owner.

## Deployment (migration `V4__claim_outbox`)

`V4` only creates the new `claim_outbox` table (`CREATE TABLE IF NOT EXISTS`). No existing table or row is
touched.

1. **Back up** `claim_service_db` (`mysqldump`, as in Phase 4 §1).
2. **Make sure the Phase 4 baseline is done** for that database: `flyway_schema_history` exists with V1–V3
   successful. If it is not, follow Phase 4 first. V4 then runs automatically on the next start; **do not**
   set `BTC_FLYWAY_BASELINE_EXISTING` again.
3. **Deploy and start claim-service.** Expect `Migrating schema ... to version "4 - claim outbox"`, after which
   Hibernate validates the table.
4. **Verify:**
   ```sql
   SELECT version, success FROM claim_service_db.flyway_schema_history ORDER BY installed_rank;  -- 4, 1
   ```

Releases of claims rejected or deleted **before** this upgrade are not in the outbox. Phase 4 released them
directly, and any that failed were logged then. Run the reconciliation query above once after upgrading.

**Rollback:**

1. Deploy the previous release.
2. Remove the table and its history row:
   ```sql
   DROP TABLE claim_service_db.claim_outbox;
   DELETE FROM claim_service_db.flyway_schema_history WHERE version = '4';
   ```
   First release any pending events' locks manually (§5 of the Phase 4 runbook), or they will be lost.

## Tests

| Suite | What it covers |
|---|---|
| `ClaimOutboxIntegrationTests` (H2) | Success, downtime with backoff and recovery, restart and abandoned leases, duplicate delivery, the active-claim guard, the retry limit with manual recovery, permanent refusal, and 4 concurrent workers delivering each event exactly once. |
| `ClaimOutboxBackgroundDeliveryTests` | The real after-commit dispatch and scheduled poll. |
| `ClaimWorkflowIntegrationTests` | The outbox row commits with a reject or delete (also while expense-service is down) and disappears on rollback. |
| `ClaimOutboxMySqlTests` | The same outbox suite on MySQL with Flyway and `validate`. Enable it with `BTC_IT_MYSQL_*` against a disposable server only (Phase 4 §7). |
