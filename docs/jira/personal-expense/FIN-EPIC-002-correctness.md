# FIN-EPIC-002 — Financial correctness and reconciliation

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |

## FIN-006 — Apply finance-specific debit and credit rules

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. Cash/bank expense decreases available money; income increases it.
2. Credit spending increases outstanding liability; liability payment decreases it.
3. Rounding, currency, overdraft, credit-limit, negative-balance, and overpayment policies are explicit and deterministic.
4. Every applied financial movement references its event and account.

## FIN-007 — Reconcile an account as a dated fact

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. A supplied balance and effective date create a reconciliation observation rather than overwriting history.
2. Current balance derives from the reconciliation and later movements.
3. Conflicting/backdated reconciliation shows expected, stated, and variance and follows a confirmation policy.

## FIN-008 — Enforce financial invariants

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. Applied transactions have their expected movement sets with valid references.
2. Known balance equals its reconciliation/opening value plus later signed movements.
3. Unsupported negative balances/limits are rejected atomically.
4. PostgreSQL integration and seeded randomized scenarios prove replay/idempotency and report reproducible failures.
