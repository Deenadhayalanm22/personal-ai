# FIN-EPIC-002 — Expense-data correctness

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |

## FIN-003 — Enforce transaction invariants

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. Expense amounts are positive and retain their supplied currency-independent numeric value.
2. Required classification and transaction date fields are validated before persistence.
3. Confirmation is idempotent at the conversation boundary and creates no balance side effects.
4. PostgreSQL integration tests verify persistence, correction history, and analytics totals.
