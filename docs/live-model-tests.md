# Live model conversation tests

`LiveModelIT` sends messages through the WhatsApp webhook, calls the real OpenAI Responses API, executes the normal deterministic financial path, and captures outbound WhatsApp messages through local WireMock.

## Prerequisites

- PostgreSQL test database available using `src/test/resources/application-test.properties`.
- WireMock available at `http://localhost:9091`.
- A valid `OPENAI_API_KEY` environment variable.

## Run

### Local env file

Copy `.env.live-model.example` to `.env.live-model`, add your key, and run:

```bash
./scripts/run-live-model-test.sh
```

`.env.live-model` is ignored by Git. The runner exports its values only to the Maven test process.

### Direct environment variables

```bash
RUN_LIVE_MODEL_TESTS=true \
OPENAI_API_KEY='your-key' \
LIVE_MODEL_NAME='gpt-4.1-mini' \
./mvnw -Dtest=LiveModelIT test
```

Omit `LIVE_MODEL_NAME` to use the configured default. The test is disabled unless `RUN_LIVE_MODEL_TESTS=true`, so normal builds never spend API credits.

The suite asserts semantic behavior and persisted financial state rather than exact model JSON or exact conversational wording. Network failures should be retried by rerunning the test; semantic assertion failures should be investigated rather than automatically retried.

The `live-model` profile disables Spring, Hibernate, Flyway, and application logs so the IntelliJ console shows the printed WhatsApp conversation. This affects only `LiveModelIT`; normal application logging is unchanged.

`it_live_001` reads its curated multilingual messages from the `live-model-scenarios` section of
`src/main/resources/test-prompts.yml`. It covers complete English/Tamil/Tanglish expenses, missing category,
missing payment source, missing amount, multiple expenses in one sentence, and read-only queries in all three
language styles. Each section prints a heading so model behavior is easy to review in the IntelliJ console.
