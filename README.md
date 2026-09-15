# Personal AI

This is a monorepo for the Personal AI expense assistant.

## Projects

- [`backend/`](backend/) — Spring Boot API, database migrations, and WhatsApp integration.
- [`frontend/`](frontend/) — Svelte/Vite web dashboard.

Project documentation is consolidated in [`docs/`](docs/):

- [`docs/jira/`](docs/jira/) — cross-stack feature behavior and integration-test scenarios.
- [`docs/contracts/`](docs/contracts/) — current API and integration contracts.
- [`docs/engineering/`](docs/engineering/) — backend and frontend setup guides.
- [`docs/product/`](docs/product/) — product requirements and vision documents.

## Local development

Start the API from its project directory:

```bash
cd backend
./mvnw spring-boot:run
```

Start the web app in a separate terminal:

```bash
cd frontend
npm ci
npm run dev
```

See the [backend guide](docs/engineering/backend-development.md) and [frontend guide](docs/engineering/frontend-development.md)
for configuration, testing, and deployment details.
