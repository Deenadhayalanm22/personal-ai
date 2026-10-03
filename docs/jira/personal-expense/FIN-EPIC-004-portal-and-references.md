# FIN-EPIC-004 — Portal access and reference hygiene

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | Done |
| Goal | Give an authorized user a safe web view of only their profile's financial data |

## FIN-012 — Sign in through a one-time WhatsApp link


### Acceptance criteria

1. **Given** a phone-number login request, **when** submitted, **then** it returns the same accepted message whether or not the number is registered.
2. **Given** a valid one-time link, **when** exchanged, **then** it creates an HttpOnly, scoped web session; expired/used links return `401`.
3. **Given** a session logout, **when** completed, **then** the server invalidates it and clears the browser cookie.
4. **Given** an unauthenticated API request, **when** made, **then** no domain data is returned and the browser redirects to portal sign-in.
5. **Given** an expired session on a dashboard URL, **when** the portal starts, **then** it must not mount dashboard features or make protected feature calls before redirecting to portal sign-in.
6. **Given** an existing WhatsApp user, **when** portal access is enabled on that user's `app_user` row, **then** a sign-in link may be sent; an absent or disabled user gets the same public response without a link. Existing enabled access and roles are migrated from the former `user_feature_flag` table.
7. **Given** an enabled user requests sign-in, **when** the WhatsApp message is sent, **then** the same one-time URL appears in the message body and the Open portal button so it can be copied into another browser.
8. **Given** the portal sign-in form, **when** a user enters a mobile number, **then** India (+91) is shown separately by default; only a 10-digit Indian mobile number beginning with 6, 7, 8, or 9 can be submitted, and the request includes the +91 prefix. Trying to change the country shows that more countries are coming soon.

### Integration-test scenarios

- Request links for registered/unregistered numbers and assert indistinguishable status/body.
- Exchange a token twice; assert first session works and second fails.
- Call a protected endpoint after logout and assert `401`.
- Load a dashboard URL with an expired session and assert the actions queue is not requested before the sign-in redirect.
- Migrate enabled, disabled, and super-admin access to `app_user` while retaining existing user IDs; assert only enabled users receive login links.
- Request a link for an enabled user and assert the message body contains the exact one-time URL used by the button.
- On the sign-in form, assert the India prefix and country notice; reject short, long, and invalid-start mobile numbers without a request, then submit a valid 10-digit number and assert the +91-prefixed payload.

## FIN-013 — Switch presentation profile without leakage

### Acceptance criteria

1. **Given** an authenticated session, **when** demo mode is read, **then** only `{ demoMode, canUseDemoMode }` is exposed; underlying profile IDs remain server-only. The switch is shown only to portal-enabled super admins, and demo mode changes by other users return `403`.
2. **Given** demo mode changes, **when** the portal refreshes, **then** it clears real/demo caches and reloads all sections for the selected profile. The V1 expense-chat panel is remounted, its in-memory conversation is discarded, and pending browser requests are aborted so a late reply cannot appear in the new profile.
3. **Given** a server endpoint, **when** demo mode is active, **then** its authenticated data is resolved against the selected profile consistently.
4. **Given** a user's super-admin access is removed while a demo session exists, **when** a protected endpoint is called, **then** it resolves to the session owner's real profile and reports demo mode as off.

### Integration-test scenarios

- Assert a normal user's demo profile response denies the feature and both switch directions return `403`; assert a super admin can switch in both directions.
- Assert a previously active demo session resolves to the real owner after super-admin access is removed.

## FIN-014 — Keep portal data usable offline without changing truth

### Acceptance criteria

1. **Given** a successful online load, **when** connectivity later drops, **then** the dashboard may show only cache for the same month and active profile.
2. **Given** no matching cache while offline, **when** a section opens, **then** it is unavailable rather than empty-but-authoritative.
3. **Given** connectivity returns, **when** health succeeds, **then** calendar, recent expenses, and monthly commitment are refreshed independently.
4. **Given** a previously verified profile and a matching saved month, **when** the backend is waking, **then** a read-only saved dashboard appears immediately with a connecting notice. It makes no protected feature requests until the profile request succeeds; a `401` still redirects to sign-in.
5. **Given** a sign-out or profile switch, **when** the portal opens again, **then** the previous profile's saved view is not displayed. Without a matching saved view, show a service wake-up message and retry control.

### Integration-test scenarios

- Delay the profile response after an online load; reload and assert the saved month is visible before the response, with no protected feature calls until profile verification completes.
- Return `401` for a delayed profile response; assert redirect to sign-in and no dashboard feature requests.

## AI credit administration

The **You → AI credits** section exposes active-profile balances and the last 50 ledger entries. Only an authenticated user with role `SUPER_ADMIN` can search users, grant credits, pause/resume AI access, inspect other users' credit activity or reconcile held provider usage. All operations authorize on the backend and record the acting administrator. Profile changes remount the credit section; the server-reported role permission controls admin visibility independently of demo-mode eligibility. The demo wallet is separate. Credit exhaustion does not block access to financial records or saved conversations. API, rollout and test requirements are in the [AI credit contract](../../contracts/ai-credits.md) and [FIN-EPIC-003 credit acceptance criteria](FIN-EPIC-003-insights.md#usage-based-money-chat-credits).

The **You** menu does not show an Expense capture shortcut. Its Your data section offers Manage names and Optional money modules; expense entry remains available through Add expense on Home. The browser name-management scenario also verifies the removed shortcut is absent and the money modules option remains visible.

You → Manage names exposes existing reference hygiene in the frontend, with merchant/account historical merges and beneficiary alias-only behavior (FIN-EPIC-002). Browser capture/confirmation resolve the active profile on every endpoint; real/demo drafts cannot be confirmed across a profile switch.

Core manual expense preparation and saved-name options remain available independently of AI credit balances/configuration. Both capture methods authenticate the active real/demo profile; no caller-selected owner is accepted. Manual controller and PostgreSQL scenarios verify profile scoping.

## FIN-014 — In-memory acceleration of online financial reads

### Acceptance criteria

6. **Given** a successful financial GET on `WebFinanceController`, **when** the same authenticated session, active profile and parameters are requested again before expiry, **then** its response is reused from application memory. Session validation still runs on every request; auth, AI-credit/admin, conversation, and mutation endpoints are not response-cached. This release requires no Redis service or Redis dependency.
7. **Given** a successful application write (including WhatsApp capture, corrections, reference merges and scheduled writes), **when** its transaction commits, **then** the instance's cached financial responses become unreachable. Rollbacks do not invalidate responses. A read already in progress cannot publish its old result into the new generation.
8. **Given** logout, expiry, a profile switch, changed role, or a different session, **when** data is requested, **then** cached financial reads cannot bypass current authentication or cross profiles/sessions. Cache identity includes the profile-local calendar date so implicit month/payment-window reads refresh at midnight.
9. **Given** an instance restart, expiry or eviction, **when** a financial read is made, **then** normal database-backed loading resumes. Default response TTL is 60 seconds to bound time-dependent views, market-data changes, external SQL changes and writes on other instances. The cache has configurable entry and byte bounds, with LRU eviction. PostgreSQL remains the source of truth.
10. **Given** a future move to Redis, **when** a Redis implementation of `CacheStore` replaces `InMemoryCacheStore`, **then** controllers, cache identities, response serialization and invalidation callers remain unchanged. The current implementation performs no network cache calls.

### Verification scenarios

- Repeat a financial read and assert its loader runs once while authentication runs twice; vary query/session/profile and assert separate loads.
- Cache data, revoke authentication, and assert the next request fails without returning the cached response.
- Commit a writer transaction and assert generation invalidation; roll back and assert no invalidation. Concurrent invalidation during a load must prevent publication of the old result.
- Round-trip generic DTO lists and dates; advance an injected monotonic clock past expiry and assert fresh loading. Failed/null/oversized responses must not be retained.
- Fill the cache past entry/UTF-8 byte bounds and assert least-recently-used eviction; create a fresh store and assert no data survives a restart.
- Simulate storage failures and corrupt JSON; assert normal reads succeed and failed storage is not retried on every request.
