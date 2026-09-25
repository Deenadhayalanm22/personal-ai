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

### Integration-test scenarios

- Request links for registered/unregistered numbers and assert indistinguishable status/body.
- Exchange a token twice; assert first session works and second fails.
- Call a protected endpoint after logout and assert `401`.
- Load a dashboard URL with an expired session and assert the actions queue is not requested before the sign-in redirect.
- Migrate enabled, disabled, and super-admin access to `app_user` while retaining existing user IDs; assert only enabled users receive login links.

## FIN-013 — Switch presentation profile without leakage

### Acceptance criteria

1. **Given** an authenticated session, **when** demo mode is read or changed, **then** only `{ demoMode }` is exposed; underlying profile IDs remain server-only.
2. **Given** demo mode changes, **when** the portal refreshes, **then** it clears real/demo caches and reloads all sections for the selected profile.
3. **Given** a server endpoint, **when** demo mode is active, **then** its authenticated data is resolved against the selected profile consistently.

## FIN-014 — Keep portal data usable offline without changing truth

### Acceptance criteria

1. **Given** a successful online load, **when** connectivity later drops, **then** the dashboard may show only cache for the same month and active profile.
2. **Given** no matching cache while offline, **when** a section opens, **then** it is unavailable rather than empty-but-authoritative.
3. **Given** connectivity returns, **when** health succeeds, **then** calendar, recent expenses, and stories are refreshed independently.
