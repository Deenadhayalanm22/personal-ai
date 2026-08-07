# EPIC-007 — Production readiness, security, and operations

| Field | Value |
|---|---|
| Issue type | Epic |
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Operate Personal AI safely, recoverably, and observably with real financial data |

## STORY-031 — Externalize secrets and harden configuration

**Status:** In Progress · **Priority:** P0

**As a** service owner, **I want** environment-specific, secret-safe configuration **so that** credentials and unsafe defaults do not reach production.

### Acceptance criteria

1. Database credentials, OpenAI keys, WhatsApp tokens, and verification secrets are supplied by a secret manager/environment and are never committed or logged.
2. Production startup fails closed when required secrets or secure settings are absent.
3. Actuator exposes only approved endpoints; `env` and other sensitive diagnostic surfaces are not publicly exposed.
4. CORS, HTTP client timeouts/retries, payload sizes, and provider base URLs have explicit environment policies.
5. Secret scanning runs in CI and a rotation procedure is tested.

### Sub-tasks

- STORY-031-A — Remove hard-coded database credentials from default configuration.
- STORY-031-B — Create dev/test/prod profiles with production-safe defaults.
- STORY-031-C — Restrict Actuator and add secret scanning.

## STORY-032 — Verify and protect external requests

**Status:** To Do · **Priority:** P0

**As a** user, **I want** only authentic, authorized requests processed **so that** attackers cannot create or read financial events.

### Acceptance criteria

1. WhatsApp webhook signatures are verified against the raw request before payload processing.
2. REST requests require authenticated identity and centralized authorization.
3. Rate limits, abuse controls, and bounded request/media sizes protect public endpoints.
4. Prompt injection or untrusted content cannot bypass deterministic mutation authorization or access another user's context.
5. Security failures are auditable without logging secrets or full financial message content.

## STORY-033 — Manage the database with reviewed migrations

**Status:** In Progress · **Priority:** P0

**As an** operator, **I want** versioned schema migrations **so that** deployments are repeatable and recoverable.

### Acceptance criteria

1. Every schema change is represented by an immutable Flyway migration reviewed with its application change.
2. Production uses migration validation and does not use Hibernate `ddl-auto=update`.
3. Migrations are tested from an empty database and from the oldest supported production version.
4. Destructive or long-running migrations include backup, compatibility, rollout, and recovery instructions.

### Sub-tasks

- STORY-033-A — Set production Hibernate schema handling to validation-only.
- STORY-033-B — Add migration upgrade-path tests.
- STORY-033-C — Document deployment and recovery procedure.

## STORY-034 — Protect and govern personal financial data

**Status:** To Do · **Priority:** P0

**As a** user, **I want** control over my personal data **so that** the service meets privacy expectations and applicable obligations.

### Acceptance criteria

1. Data inventory, purpose, lawful basis/consent, retention, and deletion policies are reviewed for each stored identifier, message, media item, transaction, and model payload.
2. Data is encrypted in transit and at rest; access is least-privilege and auditable.
3. A user can export and delete their data, subject to a documented retention/legal policy, without affecting another user.
4. Logs, traces, test fixtures, analytics, and model requests minimize or redact personal and financial content.
5. Incident response includes breach assessment, credential rotation, containment, recovery, and notification ownership.

## STORY-035 — Build reliable observability and recovery

**Status:** To Do · **Priority:** P0

**As an** operator, **I want** actionable signals and tested recovery **so that** failures do not silently lose or duplicate financial activity.

### Acceptance criteria

1. Health/readiness distinguish application, database, and required provider availability without leaking secrets.
2. Metrics and alerts cover error rate, latency, queue/backlog where relevant, delivery failures, model failures, duplicate suppression, and mutation failures.
3. Structured logs use correlation IDs across inbound message, interpretation, transaction, mutation, and outbound reply.
4. Database backup and point-in-time recovery objectives are defined and restoration is tested on a schedule.
5. Runbooks cover provider outage, model degradation, stuck processing, migration failure, duplicate delivery, and suspected incorrect mutation.

## STORY-036 — Enforce a meaningful delivery pipeline

**Status:** In Progress · **Priority:** P1

**As an** engineering team, **I want** CI/CD to block unsafe changes **so that** releases are reproducible and financially correct.

### Acceptance criteria

1. Pull requests run compilation, unit tests, PostgreSQL integration tests, migration tests, dependency/secret/security checks, and relevant semantic fixtures.
2. Scheduled randomized tests execute real invariant assertions; a workflow name alone is not considered coverage.
3. Build artifacts are versioned, traceable to source, and promoted between environments rather than rebuilt differently.
4. Deployment supports rollback or forward recovery and verifies health plus a non-mutating smoke test.
5. Live-model tests remain explicitly opt-in to prevent unplanned cost and nondeterministic merge failures.
