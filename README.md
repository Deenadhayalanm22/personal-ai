# Personal AI

This is a monorepo for the Personal AI expense assistant.

## Projects

- [`backend/`](backend/) — Spring Boot API, database migrations, and WhatsApp integration.
- [`frontend/`](frontend/) — Svelte/Vite web dashboard.

Project documentation is consolidated in [`docs/`](docs/):

- [`docs/backend/`](docs/backend/) — backend architecture, APIs, test plans, and delivery notes.
- [`docs/frontend/`](docs/frontend/) — frontend contracts, behavior notes, and design artifacts.
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

See the [backend guide](docs/backend/README.md) and [frontend guide](docs/frontend/README.md)
for configuration, testing, and deployment details.
