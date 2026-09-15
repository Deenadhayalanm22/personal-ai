# Personal AI

This is a monorepo for the Personal AI expense assistant.

## Projects

- [`backend/`](backend/) — Spring Boot API, database migrations, WhatsApp integration, and backend documentation.
- [`frontend/`](frontend/) — Svelte/Vite web dashboard.

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

See each project's README for configuration, testing, and deployment details.
