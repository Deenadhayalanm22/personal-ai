# FIN-EPIC-003 — Personal spending calendar and insights

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |
| Goal | Explain recorded spending without claiming information the user has not provided |

## FIN-009 — Browse a monthly spending calendar

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** a valid `YYYY-MM` month, **when** the calendar is requested, **then** it returns profile currency/timezone, monthly totals, and one aggregate per recorded day.
2. **Given** no expenses for a day, **when** it is selected in the portal, **then** the user can start a time-limited WhatsApp handoff for that past date; future dates are rejected.
3. **Given** a month/date expense query, **when** it is requested, **then** only visible expenses belonging to the active profile are returned with filter summary and cursor metadata.
4. **Given** an invalid/missing calendar month, **when** requested, **then** it returns `400 INVALID_MONTH`.

### Integration-test scenarios

- Seed expenses across days and assert aggregate totals, highest day, and timezone-aware dates.
- Request a valid missing-date context, then assert a future date and invalid timezone fail.
- Verify a session cannot read another profile's calendar or expense list.

## FIN-010 — Render explainable money stories

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** a selected month, **when** stories exist, **then** the response contains the selected month, currency, timezone, and only the story cards/evidence generated for that user.
2. **Given** a story with multiple cards, **when** the portal opens it, **then** cards are ordered by `sequence` and evidence can be opened only from the supplied action.
3. **Given** an expense edit or soft deletion, **when** an affected month is re-evaluated, **then** story change tracking ensures stale insight data is not treated as final.
4. **Given** no eligible story, **when** the endpoint is read, **then** it returns an empty `stories` array rather than invented guidance.

### Integration-test scenarios

- Seed a known fixture, request monthly stories, and assert period, card order, evidence transaction IDs, and display components.
- Mutate an evidence expense and assert subsequent story generation/revision changes or stale state according to service contract.

## FIN-011 — Keep insight scope honest

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. The calendar and stories describe recorded expense data, not account balances, income, transfers, or reconciliation status.
2. A story card's copy may explain deterministic values but cannot alter the values, period, evidence, or currency returned by services.
3. Missing data produces an empty/unavailable state, not a fabricated estimate.
