# Phase 6: BTC Flow UI redesign, global loader and password recovery

This phase changed the frontend only. Backend services, security, schemas and APIs are unchanged.

## Design system (`frontend/src/styles.css`)

**Tokens.** Every screen uses the CSS custom properties defined on `:root`:

| Group | Tokens |
|---|---|
| Brand | `--brand`, `--brand-ink`, `--brand-accent`, `--brand-soft` |
| Neutrals | `--bg`, `--surface*`, `--border*`, `--text*` |
| Status | `--success`, `--warning`, `--danger`, `--info`, each with a `-soft` variant |
| Shape and depth | `--radius-*`, `--shadow-*`, `--focus-ring` |

* Angular Material is themed through its `--mat-sys-*` system tokens, so no component overrides are needed.
* The older variable names (`--muted-text`, `--heading`, `--brand-1`, `--brand-2`) remain as aliases.

**Shared classes.** Pages reuse these classes, which are restyled once in the global stylesheet:

| Purpose | Classes |
|---|---|
| Page layout and cards | `page-toolbar`, `page-kicker`, `section-card`, `table-card`, `form-page-card`, `form-grid`, `action-row` |
| States | `status-badge status-<value>`, `empty-state` |
| Auth screens | `auth-*` |

**Styling rules:**
* Solid surfaces only: no glassmorphism, decorative gradients or floating shapes.
* `:focus-visible` rings on all interactive elements.
* Status colours meet WCAG AA contrast.
* `prefers-reduced-motion` is respected everywhere.

## Global loader

| Piece | Role |
|---|---|
| `shared/components/btc-loader/` (`BtcLoaderComponent`) | The single loader: a dot orbiting the BTC Flow mark, pure CSS, `role="status"` with `aria-live="polite"`. Two modes: `overlay` (full page, plus a top progress line) and `inline` (inside a card). |
| `AppComponent` | Renders the overlay **once**, from `LoadingService.visible()`, so concurrent requests never stack overlays. |
| `core/services/loading.service.ts` | Request counter. The loader appears only after `showDelayMs` (250 ms) and stays at least `minVisibleMs` (400 ms), which avoids flicker. Both are configurable through the `LOADER_TIMING` token. |
| `core/interceptors/loading.interceptor.ts` | Counts every request and uses `finalize`, so success, error and cancellation always clear the state. |
| `core/http/request-context.ts` | Per-request switches: `withLoaderMessage('Loading trips…')` sets the loader text; `inlineFeedbackRequest()` skips the global loader and the error toast for forms that show their own progress and errors (sign-in, password recovery). |

The overlay never blocks clicks (`pointer-events: none`). Request errors still appear as toasts or inline
messages.

`shared/components/brand-mark/` is the inline SVG logo, used by the shell, the auth screens and the loader.
The old Material spinner overlay (`loading-overlay`) was removed.

## Screens

| Screen | Changes |
|---|---|
| Login (`/login`) | Shared auth layout with brand panel. Labelled fields with autocomplete hints, show/hide password, field errors, inline error alert (wrong credentials, server unreachable, rate limited), busy button, **Forgot password?** link. No demo credentials. Same `AuthService.login` flow. |
| Forgot password (`/forgot-password`) | New. Email validation, sending/success/error states, back to sign in. |
| Reset password (`/reset-password?token=…`) | New. New and confirm password, with length and match validation; success and invalid-link states. |
| Shell | Lazy-loaded. Brand mark, active-route highlight with `aria-current`, section title, account menu (name, email, role, sign out). Removed the decorative "Live ops" indicator. |
| Dashboard | Role-aware greeting and copy, quick actions, stat cards with hints, upcoming trips sorted by start date, recent claims sorted by submission date, empty states with next steps. Same data and calculations. |
| Trips / Expenses / Claims / Users | Restyled through the shared classes. Shared `app-stat-card` summaries, table empty states and no-match search state, row-action labels that name the record for screen readers. Business logic untouched. |
| Confirm dialog | Danger styling, clearer copy, focus starts on Cancel. |

## Routes

| Route | Guard |
|---|---|
| `/login`, `/forgot-password` | `guestGuard` |
| `/reset-password` | none, so a reset link works even when an old session is stored |

Every route has a document title.

## Behaviour fixes

* A 401 now ends the session only when the user is signed in. Before, a signed-out visitor on any page other
  than `/login` was redirected to sign in.
* Toasts load the Material snack bar on first use.

Together with lazy-loading the shell, these changes reduce the initial bundle from 687 kB to 413 kB, and the
build has no budget warnings.

## Password reset: status

**Fully implemented** in a follow-up: the user-service API, email delivery, the `V2__password_reset_tokens`
migration, and the real Angular flow. There is no longer a feature flag. See
`docs/operations/password-reset.md` for the API, configuration, migration and test steps.

## Tests

37 unit tests (`npm test`).

| Test file | Covers |
|---|---|
| `loading.service.spec` | Delay, minimum duration, concurrency, no negative count |
| `loading.interceptor.spec` | Cleanup on success, error and cancel; concurrent requests; skip flag |
| `error.interceptor.spec` | 401 handling; inline requests skip the toast |
| `btc-loader.component.spec` | Accessibility attributes, both modes |
| `login.component.spec` | Rendering, validation, password toggle, Forgot password? link, sign-in flow, inline error |
| `forgot-password.component.spec` | Validation, disabled state, success only after the API accepts, rate-limit error, retry |
| `reset-password.component.spec` | Token stripped from the URL, validation, success, invalid token, disabled state |
| `app-shell.component.spec` | Admin and employee navigation |

## Known limitations

* **Lists show only the first 100 records.** The UI does not page through the backend's paged lists (default
  size 100, total in `X-Total-Count`).
* **Responsive layouts were not checked in a browser** at real viewport sizes. The unit tests run in jsdom.
* **The Inter font is not downloaded**, to keep the app fast; the system font stack is used instead.
