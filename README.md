## Personal AI expense assistant

This repository contains one Spring Boot application for conversational personal-expense tracking.
It accepts text or WhatsApp messages, records expenses, maintains payment-account balances, supports
safe corrections, and provides expense summaries.

WhatsApp access is stored once per number in `user_feature_flag`. The seeded `SUPER_ADMIN` can
manage normal users from WhatsApp with `add user <country-code-number>` and
`remove user <country-code-number>`. Replace the clearly marked placeholder super-admin number in
`src/main/resources/db/migration/V1__init.sql` before starting with a fresh database.

### Structure

The application uses a feature-oriented MVC layout under `src/`:

- `controller` and `conversation`: inbound HTTP/WhatsApp adapters and conversation orchestration
- `finance/expense`: expense capture, validation, categorization, and correction use cases
- `finance/account`, `finance/payment`, `finance/credit`: supporting expense features
- `finance/query`: expense reporting
- `finance/legacy`: expense transactions, payment accounts, and their balance adjustments
- `dto`: transport objects
- `llm`: model adapters; business rules remain in services

Assets, investments, lending, loans, and unrelated business domains are intentionally not part of
this baseline. Add each future domain as a feature package with its own controller/service/repository
boundary; do not create another Maven module or duplicate the `state_change` expense transaction table.

### Run

```bash
./mvnw spring-boot:run
```

Run unit tests with `./mvnw test`. Integration tests require the infrastructure described in
`src/test/resources/infra/podman-compose.yml` and run through `./mvnw verify -Pintegration`.
