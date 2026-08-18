# Cooking AI

WhatsApp-first AI cooking coach built with Spring Boot, PostgreSQL, Meta's WhatsApp Cloud API,
and OpenAI's Responses API.

The MVP ships one curated, versioned chicken-biryani recipe. Deterministic application code owns
ingredient scaling and session progress; OpenAI answers contextual questions without being allowed
to silently change the canonical recipe.

## MVP flow

Send messages such as:

```text
start chicken biryani with 500 g rice and chicken
ingredients
ready
done
repeat
progress
pause
resume
the masala is too watery
cancel
```

One active cooking session is persisted per user. WhatsApp text, interactive replies, and confirmed
voice-note transcriptions all use the same session engine. Duplicate webhook message IDs are ignored.

## Configuration

Required environment variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/cooking_ai
DB_USERNAME=postgres
DB_PASSWORD=postgres
OPENAI_API_KEY=...
WHATSAPP_ACCESS_TOKEN=...
WHATSAPP_PHONE_NUMBER_ID=...
WHATSAPP_VERIFY_TOKEN=...
```

Optional variables include `OPENAI_MODEL`, `OPENAI_TRANSCRIPTION_MODEL`, `OPENAI_BASE_URL`, and
`WHATSAPP_API_BASE_URL`.

The webhook endpoint is `GET/POST /webhook/whatsapp`. Before piloting, grant the first number access
in `user_feature_flag`; a commented example is included in the initial Flyway migration.

## Run and test

```bash
./mvnw test
./mvnw spring-boot:run
```

### Live biryani conversation

Start the local PostgreSQL and WireMock services, then opt in to the real-model test:

```bash
docker compose -f src/test/resources/infra/podman-compose.yml up -d
RUN_LIVE_MODEL_TESTS=true OPENAI_API_KEY=... ./mvnw verify -Pintegration \
  -Dit.test=CookingCoachLiveIT
```

The test prints the complete cook/coach transcript. OpenAI is called only for the contextual
layering question; WhatsApp remains local through WireMock.

## Structure

- `conversation`: WhatsApp transport, idempotency, access control, and voice transcription
- `cooking/recipe`: curated recipes and deterministic scaling
- `cooking/session`: resumable cooking progress
- `cooking/coach`: command handling, recovery guidance, and grounded AI advice
- `llm`: shared OpenAI configuration and telemetry
