# FIN-EPIC-003 — Personal spending insights

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |

## FIN-009 — Answer expense and income questions

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. Time, category, merchant, and account filters resolve without authorizing a movement.
2. Results are calculated from persisted events by deterministic code and rendered in the user's language.
3. No matches, ambiguous scope, and unlinked activity are disclosed accurately.

## FIN-010 — Explain balance confidence and cash flow

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. Reports distinguish recorded, linked, and reconciled facts.
2. Exact balance is withheld for unreconciled accounts and reconciliation is offered.
3. Transfers are excluded from income/spending while included in account movement views.

## FIN-011 — Summarize liabilities and assets

**Status:** In Progress · **Priority:** P2

### Acceptance criteria

1. Liability outstanding/payment history and asset quantity/value are deterministic.
2. Missing schedules, prices, currencies, or stale valuations are disclosed; unsupported precision is not presented as fact.
3. Model explanations cannot alter calculated numbers.

## FIN-012 — Track upcoming card payments

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. Card reminders use the recorded outstanding balance, due day, and user timezone; missing due days and zero balances are disclosed accurately.
