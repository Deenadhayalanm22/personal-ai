# FIN-EPIC-001 — Expense capture

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |

## FIN-001 — Record an expense progressively

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. The application extracts and validates expense facts supplied in the incoming message.
2. Missing core transaction fields may be clarified before confirmation.
3. A stated payment source is retained as plain transaction metadata.
4. Confirmation saves one expense record and does not create or update an account, balance, ledger movement, or setup state.
5. The original message is retained for traceability.

## FIN-002 — Correct recorded expenses

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. Authenticated users may reclassify or void their expense records.
2. Corrections preserve history through superseded and replacement transaction rows.
3. Correction logic never mutates external account state.
