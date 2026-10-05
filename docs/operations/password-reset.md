# Password reset (forgot password)

user-service owns users and passwords. The API gateway routes `/auth/**` to it. Only
`POST /auth/login`, `POST /auth/forgot-password` and `POST /auth/reset-password` are public; every other
endpoint still requires a JWT.

## API

### `POST /auth/forgot-password`

Request body: `{ "email": "user@example.com" }`

| Status | Body or meaning |
|---|---|
| **202** | `{ "message": "If an account exists for that email, you will receive password-reset instructions." }` Returned for **every** valid address, whether or not it has an account. |
| 400 | The email is missing or malformed. |
| 429 | More than 10 requests from one client IP within 15 minutes. |
| 503 | Email delivery is not configured. This does not depend on the address, so it reveals nothing. |

### `POST /auth/reset-password`

Request body: `{ "token": "...", "newPassword": "...", "confirmPassword": "..." }`

| Status | Body or meaning |
|---|---|
| **200** | `{ "message": "Your password has been reset. You can now sign in." }` No JWT is returned and the user is not signed in. |
| 400 | Password rules not met (8–72 characters, at most 72 bytes, not only spaces), or the two passwords do not match. |
| 404 | The link is invalid, was already used, or was superseded by a newer request. |
| 410 | The link has expired. |
| 429 | More than 20 attempts from one client IP within 15 minutes. |

## How it works

**Tokens**
* 32 bytes from `SecureRandom`, Base64url-encoded (43 characters).
* Only the SHA-256 hash is stored, in `password_reset_tokens.token_hash`.
* Lifetime is `BTC_PASSWORD_RESET_TTL` (default 15 minutes).
* A new request revokes the user's earlier open tokens.

**Reset**
* In one transaction, the server:
  1. marks the token used, with a conditional `UPDATE` (`used_at IS NULL AND revoked_at IS NULL AND expires_at > now`);
  2. stores the BCrypt hash of the new password;
  3. revokes the user's other tokens.
* Because the `UPDATE` is conditional, concurrent attempts with the same token produce exactly one success.

**Email**
* Sent through Spring Mail on a background thread **after** the request has committed and been answered, so
  timing and response are the same for known and unknown addresses.
* If delivery fails, the unsent token is revoked and only the exception class is logged.
* The link is `<BTC_FRONTEND_BASE_URL>/reset-password?token=…`.
* The email contains BTC Flow branding, the link, its expiry, and an ignore notice. It contains no account data.

**Abuse protection**
* Per client IP: requests over the limit get 429.
* Per address: 3 emails per 15 minutes. Extra requests still get the generic 202 but send nothing.
* The client IP is the last `X-Forwarded-For` hop, which the gateway appends; hops a client adds earlier in the header are ignored.

**Logging**
* Logs contain user ids only: never addresses, tokens, links or passwords.
* JavaMail debug output is off.

**Frontend**
* `/forgot-password` and `/reset-password` are public routes.
* The token is removed from the address bar immediately and kept only in memory.
* After a successful reset the form is cleared and the user goes to `/login`, which shows a confirmation.

## Configuration (user-service environment)

These values are mapped in `config-server/config-repo/user-service.properties` and documented in
`.env.example`.

| Variable | Required | Meaning |
|---|---|---|
| `BTC_MAIL_HOST` | yes | SMTP host |
| `BTC_MAIL_PORT` | no (587) | SMTP port |
| `BTC_MAIL_USERNAME` / `BTC_MAIL_PASSWORD` | for authenticated SMTP | SMTP credentials (secret) |
| `BTC_MAIL_FROM` | yes | Sender address |
| `BTC_FRONTEND_BASE_URL` | yes | Angular origin, e.g. `http://localhost:4200` locally or `https://…` in production |
| `BTC_MAIL_STARTTLS` / `BTC_MAIL_SMTP_AUTH` | no (true/true) | Set to `false` only for a local test SMTP server |
| `BTC_PASSWORD_RESET_TTL` | no (`PT15M`) | Link lifetime, between 5 minutes and 2 hours |

If any required value is missing, user-service logs a warning at startup naming the missing variables, and
forgot-password answers 503. The app never pretends to send email.

## Database migration `V2__password_reset_tokens` (user-service)

The migration only adds a new table, `password_reset_tokens`:

| Column | Notes |
|---|---|
| `id` | Primary key |
| `user_id` | Foreign key to `users`; deleting a user deletes their tokens |
| `token_hash` | `varchar(64)`, unique |
| `expires_at`, `created_at` | `datetime(6)`, stored in UTC |
| `used_at`, `revoked_at` | Null while the token is open |

The table also has an index on `user_id`. Existing data is untouched.

**Applying it to your database** requires your approval. **Do not restart user-service on this code before
then.**

1. Run a read-only check of `user_service_db.flyway_schema_history`. At the last inspection it **did not
   exist**.
2. Back up the database:
   `mysqldump --single-transaction -u <user> -p --databases user_service_db > before-v2.sql`
3. Start user-service:
   * **If there is no history yet** (database created before Flyway): start it once with
     `BTC_FLYWAY_BASELINE_EXISTING=true`. It records baseline 1 and applies V2. Then remove the variable.
   * **If V1 is already recorded:** start it normally.
4. Verify:
   ```sql
   SELECT version, type, success FROM user_service_db.flyway_schema_history;  -- 1 BASELINE 1, 2 SQL 1
   SELECT COUNT(*) FROM user_service_db.users;  -- unchanged
   ```

**Rollback:** restore the backup, or run
`DROP TABLE password_reset_tokens; DELETE FROM flyway_schema_history WHERE version = '2';` and deploy the
previous build.

## Testing the full flow with a test email account

1. Choose an SMTP sandbox, such as a provider's test inbox or a dedicated test mailbox. Never use a real
   mailbox's password in shared files.
2. Set the `BTC_MAIL_*` variables and `BTC_FRONTEND_BASE_URL=http://localhost:4200` in user-service's run
   configuration.
3. Apply the migration as described above, then restart user-service and the api-gateway.
4. Open `http://localhost:4200/forgot-password` and enter the email of a test user. You should see the
   generic confirmation.
5. Open the email in the sandbox and follow the link.
6. Choose a new password (8–72 characters). You land on sign-in with a confirmation.
7. Sign in with the new password; the old password should fail.
8. Opening the same link again should show "invalid or has already been used".
9. Wait 15 minutes, then try a fresh link: it should show "has expired".

## Known limitations

* **Rate limits are in memory, per user-service instance.** They reset on restart. Use a shared store such as
  Redis or the database if user-service is scaled out.
* **Existing JWTs stay valid after a reset**, for up to their 8-hour lifetime. Tokens are stateless HS256 and
  are checked by every service, and nothing tracks when a password changed.

  **Proposed smallest safe fix:** add `users.password_changed_at`, put it into tokens as a claim, and reject
  tokens issued before it. The gateway, or every service, would need to look it up, for example from a
  short-TTL cache served by user-service. Until then, shortening the JWT lifetime limits the exposure.
* **Delivery failures are not reported to the requester**, by design, since reporting them would reveal that
  the account exists. Operators see a `WARN` log line with the user id.
