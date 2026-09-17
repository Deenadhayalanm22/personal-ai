# Expense AI frontend

A Svelte/Vite dashboard that authenticates users from a single-use WhatsApp magic link and displays their monthly expenses.

## Local development

Requires Node.js 20.19 or newer.

```bash
cp .env.example .env.local
npm ci
npm run dev
```

`VITE_API_BASE` must point to the backend origin. It defaults to `http://localhost:8080` when omitted.

## Authentication flow

1. Open `/access?token=<magic-link-token>`.
2. The app exchanges the token through `POST /api/web/auth/magic-link`.
3. The backend sets the HttpOnly `WEB_SESSION` cookie.
4. The token is removed from the address bar and browser history.
5. The app loads `GET /api/v2/web/expenses/monthly` with cookies included.

Authenticated users can also open `/` directly while their session cookie remains valid. A month picker loads `GET /api/v2/web/expenses/monthly?month=YYYY-MM`.

## Deployment

The host must serve `index.html` for `/`, `/access`, and `/privacy-policy` because routing is handled by the client. Configure the backend CORS and cookie settings for the deployed frontend origin as described in the backend contract.

For Render, `render.yaml` includes the required SPA rewrite from `/*` to `/index.html`. The build also emits physical `access/index.html` and `privacy-policy/index.html` entry points so these routes work on manually created static sites before a rewrite is configured.

## Build

```bash
npm run build
```

## Browser regression tests

Playwright is the browser-regression runner. It starts Vite automatically, uses Chromium, and retains a screenshot, video, and trace when a retry fails.

```bash
npm ci
npx playwright install chromium
npm run test:e2e
```

The suite resets only the repository's disposable Podman Compose PostgreSQL volume, then starts Spring Boot in the `e2e` profile and the Vite frontend. It uses real API calls, Flyway migrations, and persisted data; it does not intercept or mock backend requests. The initial scenario covers a protected portal journey for a due monthly loan EMI: the Monthly Commitment story opens the included loan occurrence, an explicit **Mark May EMI paid** persists that occurrence, and the story refreshes its progress without changing the planned commitment.

Every run retains a screenshot, video, and trace, including passing runs. Open the visual HTML report with `npm run test:e2e:report`; use `npm run test:e2e:debug` to pause and step through the browser actions interactively.
