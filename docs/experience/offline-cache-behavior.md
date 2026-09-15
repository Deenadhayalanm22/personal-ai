# Cache-first dashboard behavior

The dashboard never calls the session endpoint during startup.

1. It renders the application shell immediately.
2. It restores the last successful calendar, recent-expense, and story responses from browser `localStorage` when available.
3. It checks the public health endpoint (`VITE_HEALTH_PATH`, default `/health`) with an 8-second timeout (`VITE_HEALTH_TIMEOUT_MS` can override it).
4. When healthy, the connection indicator becomes **Online** and all three business APIs refresh in parallel. Each successful response updates the local cache.
5. When unavailable, the indicator becomes **Offline** and the cached view remains usable. A first-time offline user sees the complete navigation and empty-state layout rather than a blank/error screen.

The cache is removed on sign-out. It contains only dashboard responses, never a session token. A protected API returning `401` still routes the user to sign-in.
