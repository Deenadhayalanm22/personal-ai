# EPIC-004 — Queries, insights, and portfolio reporting

| Field | Value |
|---|---|
| Issue type | Epic |
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Answer financial questions from persisted data with transparent scope and deterministic calculations |

## STORY-016 — Query expense totals

**Status:** Done · **Priority:** P0

**As a** user, **I want** to ask how much I spent by time, category, or merchant **so that** I can understand spending without building a report.

### Acceptance criteria

1. **Given** a supported read-only expense question, **when** interpreted, **then** time range and filters are resolved and no mutation is authorized.
2. **Given** matching persisted expenses, **when** the query executes, **then** totals are calculated by application/database logic.
3. **Given** no matching records, **when** answered, **then** the assistant says no matching activity was found rather than implying missing data is zero balance.
4. **Given** a routine result, **when** rendered, **then** a deterministic language template can answer without a second model call.

## STORY-017 — Explain data scope and confidence

**Status:** To Do · **Priority:** P1

**As a** user, **I want** reports to distinguish recorded, linked, and reconciled information **so that** I know what conclusions are safe.

### Acceptance criteria

1. **Given** recorded but unlinked activity, **when** an account-specific query runs, **then** excluded/unknown scope is disclosed.
2. **Given** an unreconciled account, **when** balance is requested, **then** the assistant declines to claim an exact current balance and offers reconciliation.
3. **Given** filters or dates are ambiguous, **when** material to the answer, **then** the response asks for clarification or states the applied interpretation.

## STORY-018 — Provide loan and liability analysis

**Status:** In Progress · **Priority:** P1

**As a** user, **I want** to understand outstanding liabilities and payment history **so that** I can plan repayments.

### Acceptance criteria

1. **Given** known liability data, **when** a summary is requested, **then** outstanding value and recorded payments are calculated deterministically.
2. **Given** interest, schedule, or balance inputs are missing, **when** a projection is requested, **then** assumptions are disclosed and unsupported precision is not presented as fact.
3. **Given** a generated explanation, **when** displayed, **then** it cannot alter the underlying calculated values.

## STORY-019 — Summarize assets and total portfolio

**Status:** In Progress · **Priority:** P1

**As a** user, **I want** a portfolio summary **so that** I can see assets, liquid value, and liabilities together.

### Acceptance criteria

1. **Given** persisted containers, **when** a portfolio summary runs, **then** assets, liquid accounts, and liabilities are separated.
2. **Given** missing or stale valuations, **when** totals are shown, **then** freshness and exclusions are disclosed.
3. **Given** multiple currencies, **when** conversion data is unavailable, **then** values are not silently summed as if they share one currency.
4. **Given** asset buy/sell history, **when** performance is reported, **then** calculation methodology and fees/tax assumptions are explicit.

## STORY-020 — Add budgets, recurring activity, and proactive insights

**Status:** To Do · **Priority:** P2

**As a** user, **I want** budgets and recurring-pattern insights **so that** the assistant helps me notice changes over time.

### Acceptance criteria

1. Budget periods, category scope, rollover rules, and alert thresholds are explicit and user-editable.
2. Recurring suggestions require confirmation before future transactions are created.
3. Notifications are opt-in, rate-limited, explain their trigger, and can be disabled.
4. Insight generation never initiates payment or account mutation.
