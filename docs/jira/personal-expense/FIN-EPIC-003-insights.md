# FIN-EPIC-003 — Personal spending calendar and insights

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |
| Goal | Explain recorded spending without claiming information the user has not provided |

## Cross-stack ownership

| Layer | Implemented responsibility |
| --- | --- |
| `FinancialTransactionCalendarService` | Aggregates visible expenses by selected month and produces the per-day transaction count, total spend, and intensity. |
| `FinancialTransactionListService` | Returns recent or selected-date expense rows, ownership-scoped filter summary, and cursor metadata. |
| `PendingActionContextService` | Creates the short-lived selected-date context consumed by the next eligible WhatsApp text expense. |
| `MoneyStoriesService` and scheduler | Persist and refresh read-only monthly story snapshots with evidence. |
| `frontend/src/App.svelte` | Owns selected month, URL `?month=YYYY-MM`, online refresh, and month/profile-local cache. |
| `frontend/src/Home.svelte` | Renders calendar intensity, month summary, recent/date activity tabs, missing-transaction handoff, story filtering, story deck, and evidence. |

Future optional planning-data enrichments for this feed follow [FIN-ARCH-001 — Composable story enrichment](FIN-ARCH-001-story-enrichment.md). They must remain relevant to each story's evidence and must not make an optional input a prerequisite for normal expense insights.

## FIN-009 — Browse a monthly spending calendar

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** a valid `YYYY-MM` month, **when** the calendar is requested, **then** it returns profile currency/timezone, monthly totals, and one aggregate per recorded day.
2. **Given** no expenses for a day, **when** it is selected in the portal, **then** the user can start a time-limited WhatsApp handoff for that past date; future dates are rejected.
3. **Given** a month/date expense query, **when** it is requested, **then** only visible expenses belonging to the active profile are returned with filter summary and cursor metadata.
4. **Given** an invalid/missing calendar month, **when** requested, **then** it returns `400 INVALID_MONTH`.

### Portal behavior

1. The selected month comes from `?month=YYYY-MM` or defaults to the current local month. Changing it updates browser history and reloads calendar, recent activity, and stories.
2. Each day button uses the API-provided `intensity` (0–4) as its visual spend-depth class. The frontend does not calculate or reinterpret intensity from transaction amounts.
3. The month summary displays API totals: `totalSpend`, `transactionCount`, and `highestSpend`.
4. **Recent** displays the latest five records loaded for the selected month. Selecting a calendar day switches to the date activity tab and requests up to 50 records for that selected date.
5. The activity panel initially shows five items and can expand to all returned items. It is a presentation limit, not backend pagination.
6. A future day cannot be selected. An empty past day can open the missing-transaction flow, which creates a date context and offers the returned WhatsApp URL.
7. Editing or deleting an item refreshes calendar, recent activity, and stories so all three views converge on the updated record.

### Integration-test scenarios

- Seed expenses across days and assert aggregate totals, highest day, and timezone-aware dates.
- Request a valid missing-date context, then assert a future date and invalid timezone fail.
- Verify a session cannot read another profile's calendar or expense list.
- Render a fixture with intensities 0–4 and assert each calendar day uses the matching visual class without recalculating it.
- Select a populated day and assert a date-scoped expense request; select an empty past day and assert context creation plus WhatsApp handoff; assert future days are disabled.
- Edit/delete a displayed item and assert all three dashboard fetches are requested again.

## FIN-010 — Render explainable money stories

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** a selected month, **when** stories exist, **then** the response contains the selected month, currency, timezone, and only the story cards/evidence generated for that user.
2. **Given** a story with multiple cards, **when** the portal opens it, **then** cards are ordered by `sequence` and evidence can be opened only from the supplied action.
3. **Given** multiple available stories, **when** the portal shows its Home preview or Stories view, **then** it presents one active card in a horizontally swipeable, stacked card rail with a visible dot/count indicator and a partial next-card preview.
4. **Given** an expense edit or soft deletion, **when** an affected month is re-evaluated, **then** story change tracking ensures stale insight data is not treated as final.
5. **Given** no eligible story, **when** the endpoint is read, **then** it returns an empty `stories` array rather than invented guidance.
6. **Given** a legacy confirmed `Food & Dining / Meat, Fish & Eggs` transaction with no spending nature, **when** the database migration backfills its deterministic `ESSENTIAL` nature, **then** current snapshots for the owner become stale so the next evaluation can rebuild stories with complete classification.

### Integration-test scenarios

- Seed a known fixture, request monthly stories, and assert period, card order, evidence transaction IDs, and display components.
- Mutate an evidence expense and assert subsequent story generation/revision changes or stale state according to service contract.

## FIN-011 — Keep insight scope honest

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. The calendar and stories describe recorded expense data, not account balances, income, transfers, or reconciliation status.
2. A story card's copy may explain deterministic values but cannot alter the values, period, evidence, or currency returned by services.
3. Missing data produces an empty/unavailable state, not a fabricated estimate.
