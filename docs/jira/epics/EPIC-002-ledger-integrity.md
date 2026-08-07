# EPIC-002 — Accounts, ledger integrity, and reconciliation

| Field | Value |
|---|---|
| Issue type | Epic |
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Maintain trustworthy containers, transactions, balances, and an immutable explanation of every change |

## STORY-006 — Create and identify financial accounts

**Status:** Done · **Priority:** P0

**As a** user, **I want** to name my cash, bank, credit, loan, or asset account **so that** activity can be linked correctly.

### Acceptance criteria

1. **Given** an account type and friendly name, **when** setup is requested, **then** the account can be created without requiring an opening balance.
2. **Given** no currency is supplied for the current MVP profile, **when** an account is created, **then** currency defaults to INR.
3. **Given** balance, safe reference, credit limit, or due day is omitted, **when** valid setup is processed, **then** omission does not block creation.
4. **Given** an unknown opening value, **when** stored, **then** it remains `null` and is never normalized to zero.

## STORY-007 — Apply account-specific financial behavior

**Status:** Done · **Priority:** P0 · **Depends on:** STORY-006

**As a** user, **I want** transactions to affect each account type correctly **so that** reported balances remain meaningful.

### Acceptance criteria

1. **Given** a cash or bank expense, **when** it is applied to a known balance, **then** available value decreases by the expense amount.
2. **Given** cash or bank income, **when** it is applied to a known balance, **then** available value increases by the income amount.
3. **Given** credit-card spending, **when** it is applied, **then** outstanding liability increases rather than available cash decreasing on that card.
4. **Given** a credit-card or loan payment, **when** it is applied, **then** the funding account and liability are adjusted according to their account strategies.
5. **Given** an asset purchase or sale, **when** it is applied, **then** quantity and funding/proceeds containers change in the correct directions.

## STORY-008 — Guarantee idempotent, auditable mutations

**Status:** In Progress · **Priority:** P0

**As a** user, **I want** retries to be safe and every balance change explainable **so that** delivery failures cannot corrupt my finances.

### Acceptance criteria

1. **Given** a transaction whose mutation was already applied, **when** execution is retried, **then** no second mutation is created.
2. **Given** any applied balance or quantity change, **when** audited, **then** it references the causal transaction, container, direction, amount/quantity, and reason.
3. **Given** a failure between transaction persistence and mutation completion, **when** the operation recovers, **then** state is either atomically complete or safely retryable.
4. **Given** concurrent duplicate delivery, **when** both requests execute, **then** a database constraint or lock—not only an in-memory check—prevents duplicate effects.

### Sub-tasks

- STORY-008-A — Document and enforce database uniqueness for mutation idempotency keys.
- STORY-008-B — Add transaction-boundary and partial-failure integration tests.
- STORY-008-C — Add concurrent duplicate-delivery tests against PostgreSQL.

## STORY-009 — Reconcile balances as dated facts

**Status:** To Do · **Priority:** P1

**As a** user, **I want** to provide a current balance later **so that** the assistant can become accurate without rewriting history.

### Acceptance criteria

1. **Given** an unreconciled account, **when** the user supplies a balance and effective date, **then** a dated reconciliation event is stored.
2. **Given** earlier activity exists, **when** reconciliation is applied, **then** previous transactions and mutations are not silently overwritten.
3. **Given** a reconciled value and later mutations, **when** current balance is requested, **then** it is derived deterministically from the reconciliation point and subsequent ledger activity.
4. **Given** conflicting or backdated reconciliation, **when** submitted, **then** the assistant explains the conflict and requires confirmation before changing derived results.

## STORY-010 — Enforce financial invariants continuously

**Status:** To Do · **Priority:** P0

**As an** engineering team, **I want** executable ledger invariants **so that** financial regressions block delivery.

### Acceptance criteria

1. Every mutation references a valid transaction and container.
2. Every transaction marked applied has the expected mutation set.
3. A known balance equals its reconciliation/opening value plus subsequent signed mutations.
4. Unsupported negative balances and breached liability/quantity limits are rejected according to an explicit account policy.
5. Replaying the same scenario produces the same final state.
6. The checks run against PostgreSQL in CI, and randomized failures report a reproducible seed.

### Sub-tasks

- STORY-010-A — Agree and document overdraft, credit-limit, rounding, and currency policies.
- STORY-010-B — Build invariant assertions and canonical scenario tests.
- STORY-010-C — Build seeded randomized simulations and retain failing artifacts.
