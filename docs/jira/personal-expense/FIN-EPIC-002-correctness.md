# FIN-EPIC-002 — Expense data correctness and repair

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |
| Goal | Keep captured expense records accurate without crossing user boundaries |

## Functional boundary

This epic covers server-side invariants, browser corrections, soft deletion, and naming repair. The current implementation updates a transaction in place and records `updatedAt`; deletion sets `deletedAt`. It does **not** yet provide immutable correction/reversal history, balance reconciliation, transfers, or account ledgers.

## FIN-003 — Enforce transaction invariants

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. **Given** a create or edit with amount, **when** it is accepted, **then** the amount is positive and rounded to two decimal places.
2. **Given** a category or subcategory update, **when** it is accepted, **then** both values are canonical taxonomy entries and the pair is valid.
3. **Given** merchant or account IDs, **when** assigned, **then** they are active references owned by the authenticated user and of the matching type.
4. **Given** a duplicate confirmation boundary, **when** retried, **then** it creates no balance side effect or duplicate expense.
5. **Given** a request that violates an invariant, **when** rejected, **then** it returns a specific `4xx` error and persists no partial change.

### Integration-test scenarios

- PATCH with zero/negative amount, mismatched taxonomy, foreign merchant, and foreign account; assert expected errors and unchanged row.
- PATCH one valid field at a time and assert other fields remain unchanged.

## FIN-004 — Correct or remove a portal expense

**Status:** Done · **Priority:** P0

**As a** signed-in user, **I want** to correct an AI-captured transaction or remove a mistaken one **so that** my dashboard reflects my actual spending.

### Acceptance criteria

1. **Given** an owned visible expense, **when** one or more permitted fields are patched, **then** the response returns its updated display item and the transaction has a new `updatedAt` value.
2. **Given** an edit with no fields, invalid values, invalid category pair, or unavailable reference, **when** submitted, **then** it returns `400` and changes nothing.
3. **Given** an owned visible expense, **when** it is deleted, **then** it is soft-deleted, excluded from subsequent list/calendar reads, and returns `204`.
4. **Given** another user's or previously deleted expense ID, **when** edited or deleted, **then** it returns `404 EXPENSE_NOT_FOUND`.
5. **Given** an edit or delete that changes a selected month, **when** stories are read again, **then** the affected story state is eligible for regeneration.

### Integration-test scenarios

- Create two users, edit/delete one user's ID from the other session, and assert no cross-user read or write.
- Edit date and amount, then assert the old calendar aggregate no longer includes it and the new date does.
- Delete a transaction, inspect persisted `deletedAt`, and assert list/calendar omit it.

## FIN-005 — Normalize merchant and account references

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** a user creates a preference with entity type, primary name, and alias, **when** valid, **then** its aliases and transaction count are returned through the preference list.
2. **Given** two or more active references of one entity type, **when** merged, **then** a canonical reference retains aliases, redundant references become inactive, and merchant/account transactions are repointed.
3. **Given** a mixed type, foreign reference, inactive reference, or fewer than two unique IDs, **when** merged, **then** it fails without a partial merge.
4. **Given** beneficiary references, **when** merged, **then** aliases consolidate but no expense rows are changed because expenses do not link beneficiaries today.
5. **Given** a generic `bank account` reference, including a common spelling error, **when** only one active named bank account is available, **then** it resolves to that account; with multiple possible accounts it remains unassigned rather than creating a generic or misspelled reference.

### Integration-test scenarios

- Merge two merchant references attached to different transactions; assert both point at canonical reference and aliases are preserved.
- Assert `MIXED_ENTITY_TYPES`, `REFERENCE_FORBIDDEN`, and `REFERENCE_NOT_ACTIVE` leave all inputs unchanged.
