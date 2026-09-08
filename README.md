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
