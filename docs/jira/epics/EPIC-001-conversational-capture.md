# EPIC-001 — Conversational capture and orchestration

| Field | Value |
|---|---|
| Issue type | Epic |
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Convert natural conversation into safe, persistent, progressively enriched financial events |

## STORY-001 — Interpret one conversational turn

**Status:** Done · **Priority:** P0

**As a** user, **I want** the assistant to understand my free-form message in context **so that** I do not need to use a form.

### Acceptance criteria

1. **Given** a supported free-form turn, **when** it is interpreted, **then** the result has a turn type, intent, zero or more structured event patches, unresolved fields, evidence, ambiguity, and confidence.
2. **Given** recent conversation context, **when** interpretation runs, **then** context is bounded and includes pending events, the last question, timezone, currency, and known account summaries.
3. **Given** invalid model output, **when** parsing fails, **then** it is rejected rather than silently converted into a financial event.
4. **Given** more than one event in a message, **when** interpretation succeeds, **then** each event has an independently executable patch.

## STORY-002 — Authorize financial mutations from current evidence

**Status:** Done · **Priority:** P0 · **Depends on:** STORY-001

**As a** user, **I want** only facts I actually supplied to change my finances **so that** conversation history cannot create a false transaction.

### Acceptance criteria

1. **Given** an amount appears only in history, **when** a new event is interpreted, **then** that amount cannot authorize a new mutation.
2. **Given** a missing, invalid, or non-positive amount for a value-changing event, **when** execution is attempted, **then** no mutation is applied.
3. **Given** a read-only query, greeting, help request, or control command, **when** processed, **then** no financial mutation is authorized.
4. **Given** an interpreted event, **when** financial execution occurs, **then** authorization and calculation remain outside the LLM.

## STORY-003 — Progressively save and enrich an expense

**Status:** Done · **Priority:** P0

**As a** user, **I want** an expense saved even when optional details are missing **so that** I do not lose useful activity.

### Acceptance criteria

1. **Given** a positive amount and sufficient transaction meaning, **when** the expense is received, **then** it is recorded before optional account enrichment.
2. **Given** payment source is missing, **when** clarification is useful, **then** the assistant asks one question with Cash, Bank/UPI, and Skip paths.
3. **Given** the user chooses an account type that does not exist, **when** linking occurs, **then** a provisional container can be created without inventing a balance.
4. **Given** the user says `skip`, `later`, `not sure`, or `don't know`, **when** a follow-up is active, **then** enrichment ends and the recorded expense remains.
5. **Given** the linked container has no known value, **when** the expense is completed, **then** no balance mutation is made and the response does not claim an exact balance.

## STORY-004 — Control, interrupt, and correct a conversation

**Status:** In Progress · **Priority:** P1

**As a** user, **I want** to stop, interrupt, undo, or change an in-progress action **so that** the assistant remains controllable.

### Acceptance criteria

1. **Given** a pending follow-up, **when** the user says `cancel` or `stop`, **then** the question flow ends without deleting an already recorded activity.
2. **Given** a pending follow-up, **when** a new transaction-like sentence arrives, **then** it is handled as a new event rather than forced into the old answer.
3. **Given** a trusted button action, **when** it is received, **then** its command ID—not its displayed title—determines behavior.
4. **Given** a completed transaction, **when** the user requests Undo, **then** an auditable reversal is created without deleting history.
5. **Given** the user requests Change, **when** corrected fields are confirmed, **then** resulting mutation differences are applied exactly once and remain traceable.

### Sub-tasks

- STORY-004-A — Implement durable reversal/undo commands.
- STORY-004-B — Implement field correction with mutation compensation.
- STORY-004-C — Add expiring, session-bound action tokens for interactive controls.

## STORY-005 — Preserve ordered, durable conversation state

**Status:** In Progress · **Priority:** P1

**As a** user, **I want** related messages handled in the right order **so that** replies update the intended event.

### Acceptance criteria

1. **Given** an internal user and channel, **when** conversation state changes, **then** pending events and bounded recent turns are persisted for that session.
2. **Given** simultaneous messages for one user, **when** processed on multiple application instances, **then** ordering prevents overlapping or misapplied mutations.
3. **Given** stale pending state, **when** its configured lifetime expires, **then** it cannot authorize a new mutation and the user receives a fresh prompt.

### Sub-tasks

- STORY-005-A — Define session expiry and stale-event behavior.
- STORY-005-B — Add distributed per-user ordering/locking.
- STORY-005-C — Test concurrency and out-of-order delivery.
