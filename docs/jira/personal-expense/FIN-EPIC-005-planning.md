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
2. **Given** a partial loan update, **when** valid, **then** only supplied editable fields change; an empty update fails. The edit control is available only in the top-right of that loan's View details sheet, not on its compact card.
3. **Given** non-positive amounts/tenure, absent required values, or another user's ID, **when** requested, **then** no row is changed and the response is a specific `4xx` error.
4. **Given** an open loan-closure action, **when** completed, **then** it leaves the open-action list and the portal reloads relevant planning data.
5. **Given** a mistakenly created loan, **when** the user selects Delete in the top-right of its View details sheet, **then** only that user's loan and its EMI occurrences are removed, its commitment snapshot refreshes, the section immediately shows the remaining loans (or the empty state), and the deleted loan never appears in Closed loans.
6. **Given** a user marks the final scheduled EMI paid, **when** that payment succeeds, **then** the loan becomes `CLOSED`, disappears from active commitments, and is retained in a collapsed Closed loans group as muted history.
7. **Given** an active loan is shown, **when** the user selects `View details`, **then** the details sheet hides historical EMI rows and shows only the next payable EMI. Before its due date, its visible Pay and Skip controls are disabled; on the due date they are enabled. Skipping an EMI records it as `SKIPPED`, removes it from that month's commitment story, and extends the repayment schedule by one month. The Skip confirmation contains an optional bank-penalty amount field. A user can also pre-close an active loan by confirming a user-entered settlement amount; it immediately becomes `CLOSED`, has no future EMI or commitment entries, and retains only actual payments plus the settlement in its history. A closed loan appears in the Closed loans group at the bottom of the Loans section and offers its full EMI payment history.
8. **Given** a loan has no recorded EMI outcome, **when** the user edits it, **then** all loan fields are editable. **Given** it has a `PAID`, `SKIPPED`, or pre-closure outcome, **when** the user opens Edit, **then** original principal, EMI, tenure, and first due date are locked and the user is directed to Restructure remaining loan. A restructure preserves past outcomes, rebuilds only future unpaid EMIs from its stated effective month, records a schedule-revision history entry, refreshes commitments, and remains one visible loan rather than creating a child-loan card.

### Integration-test scenarios

- Create/list/update a loan and assert ownership, two-decimal money values, required-field validation, and that edit is offered in View details rather than on the compact card.
- Attempt cross-user update and action completion; assert no visible or persisted change.
- Delete an owned loan from View details and assert its occurrences and any open loan action are removed and it is absent from Closed loans; attempt a cross-user delete and assert no row is changed.
- Browser regression: View details shows only the next payable EMI; its Pay and Skip controls are disabled before the due date and enabled on it. A red due reminder in Monthly Commitment reviews and focuses the owning loan; pay that EMI, then skip the following due EMI and assert it leaves that month's commitment story and extends the tenure. Pay the rescheduled final EMI, then open the closed loan's full payment history. Create a second six-month loan, pay its first two EMIs, pre-close it with a settlement in month three, and assert its closed history has no future scheduled EMI rows.
- Browser regression: Before a third loan has a recorded EMI outcome, edit its original schedule. After recording an EMI, assert schedule fields are locked, restructure only its future schedule, preserve payment history, retain one loan card, and use the revised future EMI in Monthly Commitment.

## FIN-016 — Track a mutual fund and scheduled SIPs

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** a scheme chosen from search, **when** a fund is created with or without a SIP, **then** scheme code/name are stored and a duplicate scheme for the user is rejected with `409 INVESTMENT_EXISTS`. A fund without a SIP still requires positive opening units and invested amount, supports occasional lump sums and exposes a later SIP-setup action in Fund details, leaving the compact fund card free of that setup control.
2. **Given** a configured SIP, **when** it has amount, day 1–28, and start month, **then** creation yields an initial scheduled/due occurrence and the scheduler avoids duplicate current-month occurrences.
3. **Given** a SIP payment or lump sum with positive amount, date, and units received, **when** saved from Fund details, **then** NAV is calculated from amount divided by units and the transaction is `CONFIRMED` with user-entered units as its calculation source. Fund details reloads after SIP payment, clears the red due state, and shows the paid transaction immediately.
4. **Given** a due SIP already confirmed or skipped, **when** confirmation is retried, **then** it is rejected and no second investment transaction is created.
5. **Given** confirmed holdings but unavailable latest NAV, **when** listed, **then** invested value remains available while current value and P&L are `null` rather than guessed.
6. **Given** a current-month SIP is due or confirmed, **when** the user opens Mutual Funds, **then** they can confirm its allocation or correct its saved allocation there; corrections recalculate holdings from confirmed transactions. A just-confirmed allocation acknowledgement is shown only for the current Money session; after returning, the fund card stays clean. Fund details shows an edit wrench on every recorded opening holding, SIP, and lump-sum row, so the user corrects the exact investment; each correction recalculates holdings without changing the SIP plan. A SIP becomes due on its configured local SIP day; before that day it remains upcoming. A due SIP surfaced from Monthly Commitment is visually distinct and opens, scrolls to, and focuses its owning Mutual Funds row, while an already confirmed occurrence cannot be confirmed again.
7. **Given** one or more current-month SIP allocations are confirmed, **when** the Monthly Commitment story is read, **then** the due highlight is removed and the evidence row returns to the standard neutral presentation while retaining its Review link. It does not offer allocation controls or change the intended commitment total.
8. **Given** a mistakenly created mutual fund, **when** the user selects Delete stock in View details on the fund card, **then** only that user's fund and its associated investment records are removed, its commitment snapshot refreshes, and the fund remains absent after reload.

### Frozen mutual-fund commitment journey

The acceptance source is [`mutual-fund-commitment.feature`](../../../frontend/e2e/features/mutual-fund-commitment.feature). Fund details owns immutable scheme identity, edit/delete actions, the next payable SIP, Pay and Skip decisions, and full payment history. A skipped SIP retains its planned amount and due date but no paid investment facts or penalty. Actual SIP payment can differ from the planned amount; a paid or skipped occurrence cannot be decided twice. A frequency edit effective June changes only future occurrences, so a May payment and lump sum stay fixed and a monthly May 1 SIP changed to quarterly next occurs on August 1. User supplied current NAV values the existing units without rewriting past NAVs. Compact cards retain their normal border and show a small red due strip with Due now beside View details when a SIP is due, with no payment, edit, or lump-sum controls. The included-commitment row uses the same red due treatment as a loan; Review scrolls to and focuses the fund card without a visible outline. Fund details places a themed lump-sum action to the right of Total P&L, aligns Pay and Skip to the right of the red due SIP section, and shows full payment history automatically.

### Integration-test scenarios

- Create SIP with opening holding, confirm occurrence, and assert invested/units/average NAV include only confirmed rows.
- Create a fund twice and assert conflict; confirm same SIP twice and assert transaction count is one.
- Stub missing MFAPI NAV and assert detail/list expose null valuation fields.
- Browser regression: [mutual-fund-commitment.spec.js](../../../frontend/e2e/mutual-fund-commitment.spec.js) runs one home-page timeline. It deletes a mistaken fund, creates two SIPs and a fund without an SIP in April, adds that fund's ₹20,000 SIP through Fund details while asserting the card has no setup control, and verifies the ₹60,000 May runway. It checks the red due and focused Review journey on 1 May and 5 May, neutral styling after confirmation, the remaining due SIP on 12 May, and the 21 May lump sum and editable Fund-detail history. Correcting an opening-holding row recalculates holdings without changing the SIP plan.

## FIN-017 — Add and view listed stock holdings

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** a signed-in user searches at least two characters, **when** matching listed equities are returned, **then** the browser can select a symbol/name/exchange from the stock-search response.
2. **Given** a selected stock with positive share quantity and total invested amount, **when** created, **then** it is stored as the user's opening holding; a duplicate symbol is rejected with `409 STOCK_EXISTS`.
3. **Given** a tracked stock, **when** it is listed, **then** its current value and P&L use the latest market price; if price lookup is unavailable, invested value remains visible and valuation fields are `null`.
4. **Given** a mistakenly added stock, **when** the user selects Delete stock in View details, **then** only that user's holding and its investment history are removed, and the portfolio refreshes immediately and remains absent after reload. The portal supports manual purchases and corrections to recorded investments. Selling is not supported.
5. **Given** a market-data provider change, **when** the replacement is implemented, **then** portfolio services remain dependent on `StockMarketDataAdapter`, not a Yahoo-specific client.

### Integration-test scenarios

- Search, create, and list a stock; assert quantity, invested value, current value, P&L, and duplicate handling.
- Stub an unavailable price and assert that the holding remains listed with null valuation fields.

- Browser regression: [stocks.spec.js](../../../frontend/e2e/stocks.spec.js) adds ITC and Reliance through the stock picker, then verifies each latest market price and the ₹11,755 combined portfolio value.
- Browser regression covers a single stock lifecycle, including deletion from View details.

## FIN-023 — Schedule ETF monthly investments

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. **Given** an existing stock/ETF holding, **when** the user saves a positive monthly amount, day 1–28, and start month, **then** an active monthly ETF plan is persisted and contributes once to Planned investing from its start month.
2. **Given** the plan's configured local day arrives, **when** the user opens Stocks or Monthly Commitment, **then** its current occurrence is due and can be reviewed from either view.
3. **Given** a due occurrence, **when** the user confirms amount and executed market price, **then** units are calculated when absent, the occurrence becomes confirmed, the holding valuation is recalculated, and Monthly Commitment reports the allocation as completed without changing its intended total.
4. **Given** a stock holding, **when** the user selects `View details`, **then** the stock name heads the detail window, with Edit stock and Delete stock aligned below it on the right. The detail window also owns the plan bell, due confirmation and skip actions, manual purchase beside total P&L, and direct investment history. Confirm and skip remain disabled until due and after a decision.
5. **Given** an owned confirmed investment, **when** the user selects Edit stock and corrects its opening amount, date, or shares in View details, **then** the execution price and holding totals recalculate without changing the monthly plan. A manual purchase also adds shares without changing the plan. Individual history rows do not expose Edit investment.
6. **Given** a due monthly purchase, **when** it is skipped, **then** the occurrence remains skipped in history without invested shares and the next month remains scheduled.

### Browser test scenario

- [ETF monthly-plan browser regression](../../../frontend/e2e/stocks.spec.js): add a stock holding, configure its plan in View details, verify pre-due disabled actions, confirm an ITC occurrence, skip a Reliance occurrence, correct the opening holding from Edit stock, and delete ITC from View details.

## FIN-018 — Pin the live monthly commitment story

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** any signed-in profile, **when** its existing monthly Stories endpoint is read, **then** `MONTHLY_COMMITMENT` is the first story, including when no generated expense-story snapshot exists.
2. **Given** an active loan whose EMI tenure includes the current month, **when** the story is read, **then** its EMI is included once in the live monthly commitment total; closed, future, and elapsed-tenure loans are excluded.
3. **Given** an active mutual-fund SIP or ETF monthly plan whose start month has arrived, **when** the story is read, **then** its planned amount is included once; ordinary stock holdings are not included.
4. **Given** a loan or SIP is added, changed, or confirmed, **when** Stories is reloaded, **then** the current and next-month runway reflect current source records without waiting for the money-story snapshot scheduler.
5. **Given** a loan or SIP source write, **when** it commits, **then** the current and next month's canonical `monthly_financial_snapshot` rows are rebuilt in the same transaction with semantic commitment buckets and source evidence; the story reads those snapshots rather than recalculating source records on every request.
6. **Given** the Monthly Commitment story includes one or more sources, **when** the user opens `View included commitments`, **then** they see the story-scoped source list before choosing whether to review a source in its owning section.
7. **Given** a server-generated final-EMI closure action is due for a payment that has not been recorded, **when** the user opens the Monthly Commitment story, **then** that story alone exposes `Review final EMI`; it opens and highlights that loan and asks for confirmation. Marking the final scheduled EMI paid closes the loan, removes the action, and does not show a separate home-screen action queue.
8. **Given** an active loan EMI is due in the selected month, **when** the user opens Monthly Commitment, **then** it shows compact server-calculated loan-payment progress: paid of planned, remaining, and percentage. The Loans card progress counts paid EMI occurrences, so advancing the clock to an unpaid due month does not advance its completed count or bar; paying that EMI does. `View included commitments` opens a story-scoped list of every included commitment; a due loan is highlighted red there and its `Review loan` control opens, scrolls to, and focuses that loan in the Loans section, where `Due now` sits on the left and `View details` on the right of the same compact red row. Once that EMI is paid, its included-commitment row returns to normal styling. The story face does not add a direct `Review loan` control. The same story refreshes immediately without changing the planned amount. Each monthly occurrence remains independently explainable.

### Integration-test scenarios

- Create a six-month Home loan on 15 April; assert the compact current/next EMI window and May runway, review and pay the due May EMI on 5 May, and assert its persisted payment facts and refreshed 100% Monthly Commitment progress. Advance to June and assert the June occurrence is independently due while May remains paid; pay that final June EMI and assert July has neither a loan EMI nor an included loan commitment.
- [Mutual-fund commitment browser regression](../../../frontend/e2e/mutual-fund-commitment.spec.js): follow one home-page journey through mistaken-fund deletion, two April SIPs, a later SIP set from Fund details, May 1 and May 5 due/review/focus and neutral post-confirmation story states, the May 12 remaining due SIP, the May 21 lump sum, editable opening/SIP/lump-sum history rows, and the unchanged ₹60,000 SIP runway.

### Browser Gherkin specification

- [Monthly Commitment loan EMI progress](../../../frontend/e2e/features/loan-commitment.feature) → [loan Playwright regression](../../../frontend/e2e/loan-commitment.spec.js), and [Monthly Commitment mutual-fund SIP progress](../../../frontend/e2e/features/mutual-fund-commitment.feature) → [mutual-fund Playwright regression](../../../frontend/e2e/mutual-fund-commitment.spec.js), are the readable Given/When/Then companions and executable coverage for their separate journeys.

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

## FIN-022 — Project credit-card bills from captured spending

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. **Given** an account reference created by normal expense capture, **when** the user configures that account as a credit card with a card name, issuer, statement-generation day, and payment due day, **then** the setup is private to that user and validates each day as 1–28.
2. **Given** an active configured card, **when** a monthly snapshot is built for its due month, **then** it includes exactly the visible transactions recorded against that card's account reference between the previous statement cut-off and the statement cut-off preceding that due date.
3. **Given** a card spending transaction is captured, edited, or deleted, **when** the change commits, **then** current and next-month snapshots are refreshed so a qualifying upcoming bill is current without a scheduler wait.
4. **Given** a credit-card bill source is shown in commitment evidence, **when** the user opens it, **then** it identifies the card, issuer, statement period, projected due date, and aggregate amount. It is a due-bill projection, not a second expense or payment confirmation.

The due-month mapping is deterministic: if the due day is after the statement day, the statement closes in the due month; otherwise it closes in the previous month. Its billing period begins the day after the previous statement day and ends on that statement day. This avoids asking the user to manually calculate an ambiguous billing-period range.

## Story enrichment architecture

The planned way to use voluntary salary context and future planning modules in stories is documented in [FIN-ARCH-001 — Composable story enrichment](FIN-ARCH-001-story-enrichment.md). Salary is an independent optional lens, not a required progression step or a change to commitment accounting.

### Design and lifecycle

`monthly_financial_snapshot` is the one canonical per-user/per-month financial read model. It is not a second user-facing API and it does not duplicate domain ownership: loans remain in `user_loan`; SIP configuration remains in `user_investment`; the snapshot stores only the calculated monthly planning projection and its explainable source rows.

The v1 payload contains `fullIntendedCommitment` and two semantic buckets:

| Bucket | Current source records | Meaning |
| --- | --- | --- |
| `DEBT_REPAYMENTS` | Active loan EMIs whose tenure includes the snapshot month | Required debt repayment plan. |
| `PLANNED_INVESTING` | Active mutual-fund SIPs whose start month has arrived | User-selected investing plan. |
| `CREDIT_CARD_BILLS` | Visible captured expenses for each configured card's closing statement period | Aggregate bill due in the snapshot month; it is not a duplicate expense. |

The pinned `MONTHLY_COMMITMENT` story reads these snapshots and is always returned first in the existing monthly Stories response. It contains a current-month card followed by a next-month runway card, each with its own evidence list. When the user has shared an exact monthly salary, each of those two cards adds its own deterministic commitment-to-salary percentage; salary never becomes a standalone card or exposes the salary amount. A shared range is shown only as private qualitative context, without a fabricated percentage. One AI request writes the two cards together from verified facts, so the next card continues the current-card thought without a second AI round-trip; all amounts, payoff facts, and source inclusion remain deterministic. Its evidence lists the source loan/SIP rows, amounts, due dates, and labels. Each row also carries a deterministic, playful status tag: a loan nearing payoff gets its exact end month, a longer loan is marked as a long-game member, and an SIP is a future-you contribution. This keeps the detail sheet useful without adding a second AI request per row. The story does not include stock holdings, cash balances, or ordinary recorded expenses in v1.

For a loan source, the snapshot also stores deterministic payoff facts: `remainingPayments`, `endsInMonth`, and `freesFromMonth`. The AI copy generator receives only these verified facts plus bucket totals; it uses an approved Monthly Commitment prompt for a warm closed-circle voice (for example, “leaving the chat” or “escape artist”), with at most one emoji. It may not calculate, alter, or invent payoff information, and it must never joke about debt stress, missed payments, low balances, or the user.

#### Population and refresh rules

1. **Existing users:** the first monthly Stories read finds no current-month snapshot, builds it from the user’s existing source records, saves it, and returns it. No re-entry is required.
2. **New or changed sources:** loan create/update and mutual-fund SIP create/confirmation call `MonthlyFinancialSnapshotService.refreshCurrent(user)` inside the same transaction. Source and snapshot therefore commit or roll back together.
3. **Normal reads:** once present, the Stories response reads the stored snapshot rather than scanning loans and investments.
4. **Future source mutations:** any new command that changes a commitment source—loan closure, SIP pause/resume/edit, a user-managed recurring commitment, emergency reserve rule, or goal contribution—must invoke the same refresh method in its transaction.
5. **Future buckets:** Essential living, emergency reserve, goal contributions, and protection commitments extend this one payload; they must not create independent commitment tables or duplicate total-calculation logic.
