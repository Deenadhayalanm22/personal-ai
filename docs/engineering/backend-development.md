## Personal AI expense assistant

This repository contains one Spring Boot application for conversational personal-expense tracking.
It accepts text or WhatsApp messages, records expenses, maintains payment-account balances, supports
safe corrections, and provides expense summaries.

Portal access and role are stored on `app_user` as `portal_enabled` and `role`.
WhatsApp capture creates an `app_user` with portal access disabled by default. To enable an
existing user, update that row's `portal_enabled` value to `true`; use a country-code-prefixed,
digits-only WhatsApp number. Migration V32 copies existing access records, including the seeded
super admin, into `app_user` and removes the former access table.

### Structure

The application uses a conventional, focused Spring layout under `src/main/java`:

- `controller`: HTTP entry points
- `whatsapp` and `normalization`: inbound-channel and AI adapters
- `orchestration`: cross-adapter workflow coordination
- `service`: application use cases and web facade
- `domain`, `dto`, and `entity`: business types, API payloads, and JPA persistence models
- `repository`: database access
- `config`, `exception`, and `llm`: infrastructure concerns

Assets, investments, lending, loans, and unrelated business domains are intentionally not part of
this baseline. Add each future domain with its own controller/service/repository boundary; the active
expense transaction store is `financial_transaction`.

### Run

```bash
./mvnw spring-boot:run
```

Run unit tests with `./mvnw test`. Integration tests require the infrastructure described in
`src/test/resources/infra/podman-compose.yml` and run through `./mvnw verify -Pintegration`.

### Deploy to Render with Docker

Create a **Web Service** with the **Docker** runtime and configure these Build & Deploy settings:

| Render setting | Value |
| --- | --- |
| Root Directory | `backend` |
| Dockerfile Path | `Dockerfile` |
| Docker Build Context Directory | `.` |
| Docker Command | Leave empty (the image entrypoint starts the API) |
| Health Check Path | `/actuator/health` |

The Dockerfile is intentionally relative to this directory: it copies `pom.xml` and `src/` from
`backend/`, builds the Spring Boot JAR, and runs it as an unprivileged user. The application listens
on Render's assigned `PORT` automatically (falling back to `8080` locally).

Set the required production environment variables in Render, including `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `APP_WEB_BASE_URL`, `APP_MAGIC_LINK_EXPIRY`, `APP_WEB_SESSION_EXPIRY`, and
`APP_WEB_SECURE_COOKIES`. Add the WhatsApp and OpenAI variables when those integrations are enabled.

### Flyway upgrade ordering

Migration `V22__add_commitment_extra_amount.sql` shipped after V27 and V28. Databases that
already applied those versions fail startup with `Detected resolved migration not applied to
database: 22` under Flyway's default ordering. The application enables `spring.flyway.out-of-order`
so the missing migration runs while checksum validation remains enabled. V22 changes commitment
tables created by V16/V20; V27 and V28 change separate loan and investment tables.

Redeploy with this configuration. For an existing build, set `SPRING_FLYWAY_OUT_OF_ORDER=true`
in Render and redeploy. Check the startup log for successful application of V22 and the health
endpoint for successful startup. Do not ignore V22, disable validation, or rename an already
released migration: the application needs its column and table, and other databases may already
have recorded its version/checksum. Allocate future migration numbers above the highest released
version and check dependencies before introducing any further out-of-order migration.

`FlywayUpgradeIT` covers a fresh install, the V28-to-late-V22 upgrade, and repeat startup.
Run it against local test PostgreSQL with `./mvnw -Dtest=FlywayUpgradeIT test`. It uses unique
temporary schemas and drops only those schemas afterwards. Connection overrides are
`-Dmigration.test.url=...`, `-Dmigration.test.username=...`, and `-Dmigration.test.password=...`;
defaults match the local integration database on port 5433.

### In-memory financial response cache (Render)

This release caches financial GET responses inside the backend process. No Redis service, credentials, cache connection, additional container, or paid Render resource is required. The cache defaults on automatically. PostgreSQL remains the source of truth; restarts clear only disposable response JSON, and the next request reloads it.

Caching covers the financial GETs in `WebFinanceController`; session/profile checks always run. Auth, AI credit/admin, mutation and conversation endpoints remain live. Committed writable application transactions and repository saves/deletes/bulk modifications invalidate the instance's generation. Snapshot lookups `current`/`next` invalidate only when their repositories write, keeping unchanged story reads cacheable. A stale in-flight result cannot be stored after invalidation.

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_READ_CACHE_ENABLED` | `true` | Set `false` to bypass response caching. |
| `APP_READ_CACHE_TTL` | `60s` | Bounds clock-dependent views, market data and changes outside this instance. |
| `APP_READ_CACHE_MAX_ENTRIES` | `2048` | Maximum retained responses. |
| `APP_READ_CACHE_MAX_BYTES` | `33554432` | Maximum UTF-8 key/value bytes (32 MiB); Java object overhead is additional. |
| `APP_READ_CACHE_MAX_ENTRY_BYTES` | `1048576` | Larger serialized response values bypass caching. |

The store evicts least-recently-used entries when a bound is reached. Expired entries are removed on access and reclaimed before inserting new responses. Identical concurrent requests share bounded local locks. Timezone-local date participates in each identity. Failed responses and errors are not cached.

Invalidation is deliberately broad for this release, preserving correctness across dependent financial views. Each process has independent memory: writes served by another replica and out-of-band SQL changes become visible within TTL. The first uncached request still performs the original work, and caching cannot eliminate Render cold-start time.

`PortalReadCache` is a storage-independent JSON/cache-policy wrapper. `CacheStore` defines generation/get/put/evict/invalidate, and `InMemoryCacheStore` is its only implementation. To migrate tomorrow, add a Redis implementation and select it as the `CacheStore` bean; controllers, authentication scoping and invalidation callers need no changes. A Redis adapter must provide unique shared generations, expiry, bounded server memory, and never expose old-generation entries. The wrapper already falls back to uncached reads if storage fails, with a five-second retry interval and invalidation on recovery.

Run cache and web regressions with Java 21:

```bash
./mvnw -Dtest='Portal*Cache*Test,PortalCacheTransactionTest,InMemoryCacheStoreTest,WebFinanceControllerTest,WebAuthenticationServiceTest' test
```
