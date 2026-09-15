## Personal AI expense assistant

This repository contains one Spring Boot application for conversational personal-expense tracking.
It accepts text or WhatsApp messages, records expenses, maintains payment-account balances, supports
safe corrections, and provides expense summaries.

WhatsApp access is stored once per number in `user_feature_flag`. The seeded `SUPER_ADMIN` can
manage normal users from WhatsApp with `add user <country-code-number>` and
`remove user <country-code-number>`. Replace the clearly marked placeholder super-admin number in
`src/main/resources/db/migration/V1__init.sql` before starting with a fresh database.

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
