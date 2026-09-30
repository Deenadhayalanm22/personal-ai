# Usage-based money chat credits

Feature source: [FIN-EPIC-003](../jira/personal-expense/FIN-EPIC-003-insights.md) and [FIN-EPIC-004](../jira/personal-expense/FIN-EPIC-004-portal-and-references.md).

## Scope and rollout

This release meters every model call made by V1 money chat, including scope classification, tool-selection turns, follow-ups and refusals. Expense normalization, audio transcription and scheduled story generation are **not** charged to this wallet or included in its shared daily limit. The private MCP endpoint executes database tools without calling the model and does not spend chat credits. V2 has no money-chat surface.

Migration `V35` creates persistent wallets, requests, provider-call reservations, a UTC daily budget, and an append-only grant/usage/access ledger. Existing and new users start with zero credits; super admins also need a grant for their own chat. There is no automatic refill, purchase flow or unlimited admin exemption. Wallets belong to the server-resolved active profile; demo and real balances are separate. Viewing records and saved conversations remains available without credits.

Before enabling chat in an environment, configure a tariff for its `EXPENSE_CHAT_MODEL` (or fallback `OPENAI_MODEL`) and grant credits from **You → AI credits** using an authenticated super-admin profile. Input/output rates and the shared daily budget default to zero, which deliberately blocks model calls until all are configured; this is a rollout requirement for existing users too.

| Environment variable | Meaning | Default |
|---|---|---|
| `AI_CREDITS_INPUT_PER_MILLION` | Credits per million uncached input tokens | 0; setup required |
| `AI_CREDITS_CACHED_PER_MILLION` | Credits per million cached input tokens | 0 |
| `AI_CREDITS_OUTPUT_PER_MILLION` | Credits per million output tokens | 0; setup required |
| `AI_CREDITS_DAILY_LIMIT` | Shared money-chat credit budget per UTC day | 0; explicit budget required |
| `AI_CREDITS_REQUEST_LIMIT` | Maximum credits spent/reserved for one question | 10 |
| `AI_CREDITS_REQUESTS_PER_MINUTE` | Accepted new questions per profile in a rolling minute | 6 |
| `AI_CREDITS_ENABLED` | Global chat switch; false blocks new calls, never bypasses billing | true |

Rates are an operator-defined tariff, not live provider pricing. Choose a monetary value per credit and divide the configured model's provider price per million tokens by that value. Cached rate must be between zero and input rate. Update the tariff alongside model/provider changes and consistently across application instances. Each reservation snapshots the model and tariff so settlement does not reinterpret past usage at new prices. Existing daily spending is not reset by configuration changes. Settings are read at application startup.

Cost is `((input − cached) × inputRate + cached × cachedRate + output × outputRate) / 1,000,000`, rounded upward to six decimal credits per provider call. Cached tokens are a subset of input tokens, not an additional input charge. Provider-reported output usage includes whatever the provider reports as completion tokens.

## Reservation and failure behavior

Before each provider call, a short database transaction locks the daily budget and wallet, checks pause/balance/daily/per-question limits, reserves a conservative upper cost and persists the call. No provider request happens while holding database locks. Reservations use the serialized UTF-8 prompt/history/tool-schema bytes plus framing allowance, assuming the configured provider uses byte-level text tokenization; prompts exceeding the conservative 120,000-token input bound are rejected. Output is capped at 2,000 completion tokens per call. This can reject a low-balance question whose eventual actual cost might have fitted: the backend must fund the reservation first.

On a valid usage response, a separate transaction debits actual credits, releases unused reservation, increments daily spending, and writes one ledger entry. Database locks and a unique active-request constraint enforce the limits across instances. Pausing a user prevents the next provider call, including subsequent turns of a running question; it does not cancel a call already sent.

A failed question still pays for completed model calls. A locally detected missing API key releases that call's reservation with zero cost. Provider errors/timeouts, missing/invalid usage or usage exceeding the reservation retain the hold; they are never silently treated as free. The profile cannot start another question while a previous request remains running or under review. After two minutes, an administrator can reconcile a held call against verified provider usage. An abandoned request without uncertain calls recovers on the next new request after two minutes. A process crash after a reservation also requires review. No automatic time-based refund assumes the provider did no work.

Reconciliation accepts a verified credit cost between zero and the held amount, and records the actor and verification note. If provider usage exceeds the conservative bound, investigate and correct the tariff/tokenization assumption before resuming that provider; this UI cannot settle an amount above the hold. Holds remain attributed to the UTC day on which they were reserved.

## Authenticated user endpoints

All routes require `WEB_SESSION`, return `Cache-Control: no-store`, and resolve the profile on the server. Client-supplied user IDs do not select the wallet.

- `GET /api/web/ai-credits` → `{balance,reserved,available,paused,enabled,configured}`. Balance includes held credits; available is balance minus reserved. Decimal values have at most six fractional digits.
- `GET /api/web/ai-credits/permissions` → `{admin: boolean}`, true only when the authenticated profile has role `SUPER_ADMIN`. The UI uses this permission rather than demo-mode eligibility.
- `GET /api/web/ai-credits/ledger` → newest 50 `{amount,kind,note,createdAt}` entries. Grants are positive, usage negative, and access changes zero.

The [chat request](expense-chat.md) requires a UUID `requestId`. The request fingerprint binds that ID to its body and profile. Repeating a completed request returns its stored answer without another model call or debit; a changed body gets `409 AI_REQUEST_CONFLICT`; running/review requests get `409 AI_USAGE_PENDING`. A stored failure replays its error. A new intentional question/retry uses a new ID. The browser reuses the same ID for an unchanged question after an ambiguous network failure while the panel remains mounted, and refreshes the live balance after every attempt. The “Check previous answer” action can recover an ambiguous prior request even when no credits remain; it reuses the original ID and cannot fund new work. Cached response balances are historical; GET is authoritative.

## Super-admin endpoints

These require only that the authenticated profile has role `SUPER_ADMIN`. Portal-enabled status and channel are not admin authorization conditions. All other roles are treated as normal users and receive `403 ADMIN_REQUIRED`.

- `GET /api/web/ai-credits/admin/users?search=...`: literal case-insensitive profile-identifier substring, up to 100 characters; returns up to 100 `{id,channel,externalUserId,balance,reserved,available,paused}` rows. Empty search lists the first 100.
- `POST /api/web/ai-credits/admin/users/{userId}/grants`: `{requestId: UUID,amount: decimal,note: string}`. Amount is positive, at most 1,000,000 and at most six fractional digits. Note is nonblank, at most 300 characters. Identical retries are idempotent; changing grant content or actor under the same ID returns 409. Returns the updated balance.
- `PUT /api/web/ai-credits/admin/users/{userId}/access`: `{paused: boolean}`. Returns the updated balance and audits the acting admin.
- `GET /api/web/ai-credits/admin/users/{userId}/ledger`: latest 50 entries for the selected profile.
- `GET /api/web/ai-credits/admin/pending`: oldest 100 held calls older than two minutes, including reservation UUID, user ID/identifier, model, reserved credits and creation time.
- `POST /api/web/ai-credits/admin/pending/{id}/resolve`: `{amount: decimal,note: string}`. Amount is verified billed credits (zero allowed), at most the hold, maximum 1,000,000 and six fractional digits. Note is required, up to 300 characters. Returns `{resolved:true}`. A retry never debits twice.

## Errors and UI

Errors use `{code,message}`. `402 AI_CREDITS_EXHAUSTED` means insufficient available credits for the next reservation; `403 AI_ACCESS_PAUSED` means user pause; `429 AI_DAILY_LIMIT` and `AI_RATE_LIMIT` enforce shared/day and profile/minute limits; `422 AI_QUESTION_LIMIT` and `AI_REQUEST_TOO_LARGE` bound work. `503 AI_PAUSED` or `AI_CREDITS_NOT_CONFIGURED` blocks globally; `409 AI_USAGE_PENDING` or `503 AI_USAGE_PENDING` requires waiting/review. Invalid grants/notes/access use 400 and unknown user/reservation uses 404.

The chat shows available and held credits and a refresh action. Zero balance, pause, unavailable configuration, or a failed balance load disables sending while preserving drafts and saved history. Backend enforcement remains authoritative for low nonzero balances and all limits. Profile settings show own credit activity; super admins can find profiles, grant credits, inspect history, pause/resume access, and reconcile verified holds. Profile changes remount credit UI to discard stale state.

## Verification

`CreditStorePostgresTest` creates a unique schema on `EXPENSE_CHAT_TEST_DB_URL` (with `EXPENSE_CHAT_TEST_DB_USER` and `EXPENSE_CHAT_TEST_DB_PASSWORD`), migrates it, tests concurrent wallets/global budgets and settlement/replay/recovery, then drops only that schema. `AiCreditControllerTest` verifies authorization and profile isolation. Chat service/SDK tests verify the classification and answer path. `frontend/e2e/expense-chat.spec.js` covers balances, blocking, replenishment, ambiguous network retries and admin grants with mocked APIs.
