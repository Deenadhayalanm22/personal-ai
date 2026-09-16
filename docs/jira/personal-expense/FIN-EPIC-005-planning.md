# FIN-EPIC-005 — Loans and investment planning

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

## FIN-017 — Add and view listed stock holdings

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** a signed-in user searches at least two characters, **when** matching listed equities are returned, **then** the browser can select a symbol/name/exchange from the stock-search response.
2. **Given** a selected stock with positive share quantity and total invested amount, **when** created, **then** it is stored as the user's opening holding; a duplicate symbol is rejected with `409 STOCK_EXISTS`.
3. **Given** a tracked stock, **when** it is listed, **then** its current value and P&L use the latest market price; if price lookup is unavailable, invested value remains visible and valuation fields are `null`.
4. **Given** the initial stock release, **when** a holding is viewed, **then** the portal supports add and view only—there are no edit, delete, buy, or sell operations.
5. **Given** a market-data provider change, **when** the replacement is implemented, **then** portfolio services remain dependent on `StockMarketDataAdapter`, not a Yahoo-specific client.

### Integration-test scenarios

- Search, create, and list a stock; assert quantity, invested value, current value, P&L, and duplicate handling.
- Stub an unavailable price and assert that the holding remains listed with null valuation fields.

## FIN-018 — Pin the live monthly commitment story

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** any signed-in profile, **when** its existing monthly Stories endpoint is read, **then** `MONTHLY_COMMITMENT` is the first story, including when no generated expense-story snapshot exists.
2. **Given** an active loan whose EMI tenure includes the current month, **when** the story is read, **then** its EMI is included once in the live monthly commitment total; closed, future, and elapsed-tenure loans are excluded.
3. **Given** an active mutual-fund SIP whose start month has arrived, **when** the story is read, **then** its SIP amount is included once; stock holdings are not included.
4. **Given** a loan or SIP is added, changed, or confirmed, **when** Stories is reloaded, **then** the current and next-month runway reflect current source records without waiting for the money-story snapshot scheduler.
5. **Given** a loan or SIP source write, **when** it commits, **then** the current and next month's canonical `monthly_financial_snapshot` rows are rebuilt in the same transaction with semantic commitment buckets and source evidence; the story reads those snapshots rather than recalculating source records on every request.

## FIN-019 — Private income outlook

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. **Given** a user opens Your money, **when** they choose to share salary context, **then** the first question offers a monthly range and an explicit optional path for an exact figure; no exact amount is required.
2. **Given** the user chooses a range, exact amount, or skips salary, **when** saved, **then** the preference and frequency are private user-owned planning data and no income transaction, balance, or spending-story evidence is created.
3. **Given** a user has shared salary context, **when** the portal reads or saves the outlook, **then** the API returns only a masked `"**"` marker, never the saved range, exact amount, or frequency; the portal presents that marker in a compact privacy summary.
4. **Given** salary context has been saved, **when** the portal shows its compact masked summary, **then** a small adjacent edit control reopens the salary flow; the full-width setup action is shown only before salary is shared.

### Product behaviour

The initial release is deliberately limited to salary context. It uses “private estimate” language, makes the prompt skippable, and says what the information will not do. Exact salary is deliberately a secondary choice, not a required field or default. This outlook remains separate from FIN-018’s commitment snapshots until verified calculation rules are specified.

### Integration-test scenarios

- Save a salary range without an exact amount; verify it is returned only to the authenticated user.
- Save an exact salary after explicitly choosing that option; verify two-decimal storage and rejection of zero or invalid input.
- Read and save a shared salary; verify that neither response exposes the range, exact amount, or frequency and that the response contains only the masked marker.

## Story enrichment architecture

The planned way to use voluntary salary context and future planning modules in stories is documented in [FIN-ARCH-001 — Composable story enrichment](FIN-ARCH-001-story-enrichment.md). Salary is an independent optional lens, not a required progression step or a change to commitment accounting.

### Design and lifecycle

`monthly_financial_snapshot` is the one canonical per-user/per-month financial read model. It is not a second user-facing API and it does not duplicate domain ownership: loans remain in `user_loan`; SIP configuration remains in `user_investment`; the snapshot stores only the calculated monthly planning projection and its explainable source rows.

The v1 payload contains `fullIntendedCommitment` and two semantic buckets:

| Bucket | Current source records | Meaning |
| --- | --- | --- |
| `DEBT_REPAYMENTS` | Active loan EMIs whose tenure includes the snapshot month | Required debt repayment plan. |
| `PLANNED_INVESTING` | Active mutual-fund SIPs whose start month has arrived | User-selected investing plan. |

The pinned `MONTHLY_COMMITMENT` story reads these snapshots and is always returned first in the existing monthly Stories response. It contains a current-month card followed by a next-month runway card, each with its own evidence list. When the user has shared an exact monthly salary, each of those two cards adds its own deterministic commitment-to-salary percentage; salary never becomes a standalone card or exposes the salary amount. A shared range is shown only as private qualitative context, without a fabricated percentage. One AI request writes the two cards together from verified facts, so the next card continues the current-card thought without a second AI round-trip; all amounts, payoff facts, and source inclusion remain deterministic. Its evidence lists the source loan/SIP rows, amounts, due dates, and labels. Each row also carries a deterministic, playful status tag: a loan nearing payoff gets its exact end month, a longer loan is marked as a long-game member, and an SIP is a future-you contribution. This keeps the detail sheet useful without adding a second AI request per row. The story does not include stock holdings, cash balances, or ordinary recorded expenses in v1.

For a loan source, the snapshot also stores deterministic payoff facts: `remainingPayments`, `endsInMonth`, and `freesFromMonth`. The AI copy generator receives only these verified facts plus bucket totals; it uses an approved Monthly Commitment prompt for a warm closed-circle voice (for example, “leaving the chat” or “escape artist”), with at most one emoji. It may not calculate, alter, or invent payoff information, and it must never joke about debt stress, missed payments, low balances, or the user.

#### Population and refresh rules

1. **Existing users:** the first monthly Stories read finds no current-month snapshot, builds it from the user’s existing source records, saves it, and returns it. No re-entry is required.
2. **New or changed sources:** loan create/update and mutual-fund SIP create/confirmation call `MonthlyFinancialSnapshotService.refreshCurrent(user)` inside the same transaction. Source and snapshot therefore commit or roll back together.
3. **Normal reads:** once present, the Stories response reads the stored snapshot rather than scanning loans and investments.
4. **Future source mutations:** any new command that changes a commitment source—loan closure, SIP pause/resume/edit, a user-managed recurring commitment, emergency reserve rule, or goal contribution—must invoke the same refresh method in its transaction.
5. **Future buckets:** Essential living, emergency reserve, goal contributions, and protection commitments extend this one payload; they must not create independent commitment tables or duplicate total-calculation logic.
