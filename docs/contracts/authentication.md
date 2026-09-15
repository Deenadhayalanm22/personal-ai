# Authentication and session

All `/api/web/**` endpoints use the `WEB_SESSION` cookie, except public login-link and magic-link exchange. The cookie is `HttpOnly`, scoped to `/api/web`, uses configured SameSite policy, and is `Secure` when `app.web.secure-cookies=true`. A `401` from an authenticated browser call dispatches `app:unauthorized`; `App.svelte` redirects to `/portal`.

| Endpoint | Contract | Frontend owner |
| --- | --- | --- |
| `POST /api/web/auth/login-link` | Public. Body: `{ "phoneNumber": "+919876543210" }`. Always returns `202` and `{ "message": "…" }`, without revealing whether the number exists. | `Auth.svelte` validates E.164-like input and sends/resends the WhatsApp link. |
| `POST /api/web/auth/magic-link` | Public. Body: `{ "token": "…" }`. Consumes one-time token, sets `WEB_SESSION`, returns `{ "authenticated": true, "expiresAt": "ISO-8601 instant" }`. Invalid/expired/used links return `401`. | `App.svelte` on `/access?token=…`; redirects to saved destination or `/dashboard`. |
| `GET /api/web/auth/session` | Validates session; returns `{ "authenticated": true, "expiresAt": null }`. | Wrapper exists but no current component calls it. |
| `POST /api/web/auth/logout` | Invalidates the session, clears cookie, returns `204`. | `Home.svelte` settings sign-out; clears real and demo caches. |
| `GET /api/web/auth/demo-profile` | Returns `{ "demoMode": boolean }`; profile identifiers never leave server. | `App.svelte` resolves it before reading cache, preventing real cached data from appearing in demo mode. |
| `PUT /api/web/auth/demo-profile` | Body `{ "enabled": boolean }`; returns `{ "demoMode": boolean }`. Changes profile for subsequent calls. | `ProfileSettings.svelte` through `Home.svelte`; clears caches and reloads data. |

The generic login response is intentional account-enumeration protection. `VITE_API_BASE` chooses API origin and browser requests always use `credentials: 'include'`.
