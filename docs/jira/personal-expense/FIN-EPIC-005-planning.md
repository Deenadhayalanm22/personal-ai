# FIN-EPIC-005 — Loans and mutual-fund planning

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |
| Goal | Let a user record planning information separately from expense capture and inspect its stated valuation basis |

## FIN-015 — Manage user loans

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** a complete loan form, **when** created, **then** loan name, type, lender, principal, EMI, tenure, first due date, optional notes, and default `ACTIVE` status are persisted for the signed-in user.
2. **Given** a partial loan update, **when** valid, **then** only supplied editable fields change; an empty update fails.
3. **Given** non-positive amounts/tenure, absent required values, or another user's ID, **when** requested, **then** no row is changed and the response is a specific `4xx` error.
4. **Given** an open loan-closure action, **when** completed, **then** it leaves the open-action list and the portal reloads relevant planning data.

### Integration-test scenarios

- Create/list/update a loan and assert ownership, two-decimal money values, and required-field validation.
- Attempt cross-user update and action completion; assert no visible or persisted change.

## FIN-016 — Track a mutual fund and scheduled SIPs

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** a scheme chosen from search, **when** a fund is created, **then** scheme code/name are stored and a duplicate scheme for the user is rejected with `409 INVESTMENT_EXISTS`.
2. **Given** a configured SIP, **when** it has amount, day 1–28, and start month, **then** creation yields an initial scheduled/due occurrence and the scheduler avoids duplicate current-month occurrences.
3. **Given** a SIP or lump-sum confirmation with positive amount/date and NAV or units, **when** saved, **then** missing NAV/units are calculated and the transaction is `CONFIRMED` with recorded calculation source.
4. **Given** a due SIP already confirmed or skipped, **when** confirmation is retried, **then** it is rejected and no second investment transaction is created.
5. **Given** confirmed holdings but unavailable latest NAV, **when** listed, **then** invested value remains available while current value and P&L are `null` rather than guessed.

### Integration-test scenarios

- Create SIP with opening holding, confirm occurrence, and assert invested/units/average NAV include only confirmed rows.
- Create a fund twice and assert conflict; confirm same SIP twice and assert transaction count is one.
- Stub missing MFAPI NAV and assert detail/list expose null valuation fields.
