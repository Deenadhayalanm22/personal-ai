# CORE-EPIC-004 — Trust, quality, security, and operations

| Field | Value |
|---|---|
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Make the shared platform safe, measurable, recoverable, and affordable |

## CORE-016 — Isolate tenants and verify requests

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. WhatsApp signatures and REST authentication are verified before business processing.
2. Tenant/user identity is server-derived; every read/write is ownership-scoped and cross-tenant negative tests pass.
3. Rate, payload, media, and concurrency limits protect public entry points.
4. Extension code receives only authorized, minimal context and cannot query another tenant directly.

## CORE-017 — Protect personal and operational data

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. Data inventory, purpose, consent, retention, export, deletion, and media/model-transfer policies are approved.
2. Secrets are externally managed; data is encrypted in transit/at rest; logs and metrics exclude sensitive/high-cardinality values.
3. Production exposes only approved operational endpoints and uses migration validation, not automatic schema updates.
4. Backup/restore and incident response are tested on a schedule.

## CORE-018 — Evaluate language behavior and extension safety

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. Consented, redacted, reviewed corpora cover languages, voice/text, extensions, ambiguity, corrections, and adversarial inputs.
2. Reports include event/field/unit accuracy, unsafe execution, clarification, abandonment, latency, calls/tokens, and cost.
3. Deterministic tests do not depend on live-model wording; live tests are opt-in and assert semantic plus persisted outcomes.
4. A prompt, model, schema, or extension release must pass agreed regression thresholds.

## CORE-019 — Observe and recover end-to-end processing

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. Correlation links inbound delivery, interpretation, pending state, event, movement, and response without exposing message content.
2. Alerts cover delivery, model, validation, duplicate suppression, persistence, and outbound failures.
3. Runbooks cover provider outage, model degradation, stuck turns, duplicate delivery, bad extension rollout, and incorrect movement.
4. Schema and extension deployments are versioned, traceable, and rollback/forward-recovery capable.

## CORE-020 — Control model calls and cost

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. Trusted commands, routine answers, numeric pending replies, and deterministic rendering bypass the model where safe.
2. Calls, latency, input/cached/output tokens, errors, and avoided calls use bounded tags.
3. Escalation or a different model is enabled only when evaluation proves worthwhile improvement.
4. Cost is measurable per extension and tenant without placing identifiers in metric tags.
