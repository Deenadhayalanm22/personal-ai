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
7. **Given** an active loan is shown, **when** the user selects `View details`, **then** the details sheet hides historical EMI rows and shows only the next payable EMI. Its Pay and Skip controls open in the configured advance window within the due month. Skipping an EMI records it as `SKIPPED`, removes it from that month's commitment story, and extends the repayment schedule by one month. The Skip confirmation contains an optional bank-penalty amount field. A user can also pre-close an active loan by confirming a user-entered settlement amount; it immediately becomes `CLOSED`, has no future EMI or commitment entries, and retains only actual payments plus the settlement in its history. A closed loan appears in the Closed loans group at the bottom of the Loans section and offers its full EMI payment history.
8. **Given** a loan has no recorded EMI outcome, **when** the user edits it, **then** all loan fields are editable. **Given** it has a `PAID`, `SKIPPED`, or pre-closure outcome, **when** the user opens Edit, **then** original principal, EMI, tenure, and first due date are locked and the user is directed to Restructure remaining loan. A restructure preserves past outcomes, rebuilds only future unpaid EMIs from its stated effective month, records a schedule-revision history entry, refreshes commitments, and remains one visible loan rather than creating a child-loan card.

### Integration-test scenarios

- Create/list/update a loan and assert ownership, two-decimal money values, required-field validation, and that edit is offered in View details rather than on the compact card.
- Attempt cross-user update and action completion; assert no visible or persisted change.
- Delete an owned loan from View details and assert its occurrences and any open loan action are removed and it is absent from Closed loans; attempt a cross-user delete and assert no row is changed.
- Browser regression: View details shows only the next payable EMI; its Pay and Skip controls are disabled before the configured same-month advance window and enabled within it. A red due reminder in Monthly Commitment reviews and focuses the owning loan; pay that EMI, then skip the following due EMI and assert it leaves that month's commitment story and extends the tenure. Pay the rescheduled final EMI, then open the closed loan's full payment history. Create a second six-month loan, pay its first two EMIs, pre-close it with a settlement in month three, and assert its closed history has no future scheduled EMI rows.
- Browser regression: Before a third loan has a recorded EMI outcome, edit its original schedule. After recording an EMI, assert schedule fields are locked, restructure only its future schedule, preserve payment history, retain one loan card, and use the revised future EMI in Monthly Commitment.

## FIN-016 — Track a mutual fund and scheduled SIPs

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** a scheme chosen from search, **when** a fund is created with or without a SIP, **then** scheme code/name are stored and a duplicate scheme for the user is rejected with `409 INVESTMENT_EXISTS`. A fund without a SIP still requires positive opening units and invested amount, supports occasional lump sums and exposes a later SIP-setup action in Fund details, leaving the compact fund card free of that setup control.
2. **Given** a configured SIP, **when** it has amount, day 1–28, and start month, **then** creation yields an initial scheduled/due occurrence and the scheduler avoids duplicate current-month occurrences.
3. **Given** a SIP payment or lump sum with positive amount, date, and units received, **when** saved from Fund details, **then** NAV is calculated from amount divided by units and the transaction is `CONFIRMED` with user-entered units as its calculation source. Fund details reloads after SIP payment, clears the red due state, and shows the paid transaction immediately.
4. **Given** a due SIP already confirmed or skipped, **when** confirmation is retried, **then** it is rejected and no second investment transaction is created.
5. **Given** confirmed holdings but unavailable latest NAV, **when** listed, **then** invested value remains available while current value and P&L are `null` rather than guessed.
6. **Given** a current-month SIP is due or confirmed, **when** the user opens Mutual Funds, **then** they can confirm its allocation or correct its saved allocation there; corrections recalculate holdings from confirmed transactions. A just-confirmed allocation acknowledgement is shown only for the current Money session; after returning, the fund card stays clean. Fund details shows an edit wrench on every recorded opening holding, SIP, and lump-sum row, so the user corrects the exact investment; each correction recalculates holdings without changing the SIP plan. A SIP becomes actionable in the configured advance window within its scheduled local month; it remains upcoming until its due date. A due SIP surfaced from Monthly Commitment is visually distinct and opens, scrolls to, and focuses its owning Mutual Funds row, while an already confirmed occurrence cannot be confirmed again.
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
4. **Given** a stock holding, **when** the user selects `View details`, **then** the stock name heads the detail window, with Edit stock and Delete stock aligned below it on the right. The detail window also owns the plan bell, due confirmation and skip actions, manual purchase beside total P&L, and direct investment history. Confirm and skip remain disabled before the configured same-month advance window and after a decision.
5. **Given** an owned confirmed investment, **when** the user selects Edit stock and corrects its opening amount, date, or shares in View details, **then** the execution price and holding totals recalculate without changing the monthly plan. A manual purchase also adds shares without changing the plan. Individual history rows do not expose Edit investment.
6. **Given** a due monthly purchase, **when** it is skipped, **then** the occurrence remains skipped in history without invested shares and the next month remains scheduled.

### Browser test scenario

- [ETF monthly-plan browser regression](../../../frontend/e2e/stocks.spec.js): add a stock holding, configure its plan in View details, verify pre-due disabled actions, confirm an ITC occurrence, skip a Reliance occurrence, correct the opening holding from Edit stock, and delete ITC from View details.

## FIN-018 — Pin the live monthly commitment story

**Status:** Done · **Priority:** P1

### Acceptance criteria

1. **Given** any signed-in profile, **when** the monthly commitment endpoint is read, **then** the live `MONTHLY_COMMITMENT` presentation is returned without any generated spending-story snapshot.
2. **Given** an active loan whose EMI tenure includes the current month, **when** the story is read, **then** its EMI is included once in the live monthly commitment total; closed, future, and elapsed-tenure loans are excluded.
3. **Given** an active mutual-fund SIP or ETF monthly plan whose start month has arrived, **when** the story is read, **then** its planned amount is included once; ordinary stock holdings are not included.
4. **Given** a loan or SIP is added, changed, or confirmed, **when** Stories is reloaded, **then** the current and next-month runway reflect current source records without a background story scheduler.
5. **Given** a loan or SIP source write, **when** it commits, **then** the current and next month's canonical `monthly_financial_snapshot` rows are rebuilt in the same transaction with semantic commitment buckets and source evidence; the story reads those snapshots rather than recalculating source records on every request.
6. **Given** the Monthly Commitment story includes one or more sources, **when** the user opens `View included commitments`, **then** they see the story-scoped source list before choosing whether to review a source in its owning section.
7. **Given** a server-generated final-EMI closure action is due for a payment that has not been recorded, **when** the user opens the Monthly Commitment story, **then** that story alone exposes `Review final EMI`; it opens and highlights that loan and asks for confirmation. Marking the final scheduled EMI paid closes the loan, removes the action, and does not show a separate home-screen action queue.
8. **Given** an active loan EMI is due in the selected month, **when** the user opens Monthly Commitment, **then** it shows compact server-calculated loan-payment progress: paid of planned, remaining, and percentage. The Loans card progress counts paid EMI occurrences, so advancing the clock to an unpaid due month does not advance its completed count or bar; paying that EMI does. `View included commitments` opens a story-scoped list of every included commitment; a due loan is highlighted red there and its `Review loan` control opens, scrolls to, and focuses that loan in the Loans section, where `Due now` sits on the left and `View details` on the right of the same compact red row. Once that EMI is paid, its included-commitment row returns to normal styling. The story face does not add a direct `Review loan` control. The same story refreshes immediately without changing the planned amount. Each monthly occurrence remains independently explainable.
9. **Given** a user opens either Monthly Commitment runway card on desktop or mobile, **when** planned buckets are shown, **then** each bucket occupies one full-width row whose own planned amount is 100%. Current-month loan, SIP, recurring-commitment and credit-card bill bars show recorded completion against their respective planned amounts. Completing a recurring commitment preserves its source in the current-month projection even after its next date advances; its planned total stays fixed and the bar reaches 100% for that completed occurrence. Other current buckets and next-month buckets show a full planned bar, never as completion. Next-month card bills point to recorded payments in Accounts; sources without settlement tracking explicitly say payment is untracked. Completed rows use green and partial completion uses teal. A separate 100% stacked bar shows each bucket’s share of the full planned commitment when exact salary is absent. When exact salary context is available, the stacked bar allocates the estimated monthly salary across planned buckets and shows the exact currency amount left or short. A range never produces a numerical salary calculation. Salary is an estimate and is never presented as an account balance. The reader does not overflow horizontally on a narrow phone.

Home exposes this reader through the below-calendar overview’s (inside the shared Month at a glance card) `View monthly plan` link. Spending includes recurring expense payments once. Still to pay is calculated from unpaid canonical bill/loan/card and SIP/stock/ETF sources, with completed/skipped occurrences excluded by status rather than subtracting actual spending. Planned investing is an included subtotal of Still to pay; earmarked savings remain separately labelled. Detailed progress, salary context and next-month sources stay inside the reader.

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

The income estimate combines salary, rental receipts, business income and other sources into one typical monthly total; source tagging is not required. It uses “private estimate” language, makes the prompt skippable, and says what the information will not do. Exact salary is deliberately a secondary choice, not a required field or default. This outlook remains separate from FIN-018’s commitment snapshots until verified calculation rules are specified.

### Integration-test scenarios

- Save a salary range without an exact amount; verify it is returned only to the authenticated user.
- Save an exact salary after explicitly choosing that option; verify two-decimal storage and rejection of zero or invalid input.
- Read and save a shared salary; verify that neither response exposes the range, exact amount, or frequency and that the response contains only the masked marker.

## FIN-022 — Project credit-card bills from captured spending

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. **Given** an account reference created by normal expense capture, **when** the user configures that account as a credit card with a card name, issuer, statement-generation day, and payment due day, **then** the setup is private to that user and validates each day as 1–28.
2. **Given** an active configured card, **when** a monthly snapshot is built for its due month, **then** it includes exactly the visible transactions recorded against that card's account reference from the previous bill-generation day inclusive to the bill-generation day preceding that due date exclusive. Generation-day purchases belong to the following bill.
3. **Given** a card spending transaction is captured, edited, or deleted, **when** the change commits, **then** current and next-month snapshots are refreshed so a qualifying upcoming bill is current without a scheduler wait.
4. **Given** a credit-card bill source is shown in commitment evidence, **when** the user opens it, **then** it identifies the card, issuer, statement period, projected due date, and aggregate amount. It is a due-bill projection, not a second expense or payment confirmation.

5. **Given** purchases on a configured card, **when** Home displays spending, **then** they remain expenses on their purchase dates with an included card-purchase subtotal. Card bills explain “purchases already counted in spending”; spending and payments due are never added together as total expenses.
6. **Given** a closed captured statement bill, **when** the user records a full or partial payment with its actual date, **then** a separate settlement is saved without an expense transaction. Remaining due decreases by that amount, the planned bill stays in commitment totals, and current card progress advances. Accounts exposes the explicit payment form and dated history; Activity labels the settlement separately.
7. **Given** a payment retry, **then** an identical card/request ID writes once; changed details conflict. Foreign/inactive cards, future/pre-statement payment dates, open statements, nonpositive/overprecision amounts are rejected atomically. Concurrent payments serialize per card. Billing account/cycle changes after settlements are rejected; display names stay editable.
8. **Given** an actual payment exceeding captured purchases (including zero captured purchases or additional payments after coverage), **then** save its full amount and show `max(paid - captured, 0)` as Unmatched payment in Accounts for future review, without fabricating an expense or carrying an inferred balance into another bill. Payment recording remains available for closed statements. **Given** an earlier payment and a later purchase correction, **then** the payment remains intact, the captured bill is recalculated and remaining is floored at zero. Payment recording does not import an issuer statement, execute a bank payment or maintain a bank balance. V1 does not support payment undo or issuer interest/adjustments.

### Settlement verification

`CreditCardBillPostgresTest` verifies October purchases and November partial/full settlements across real PostgreSQL migrations, concurrent identical retries and distinct actual payments, payments above captured purchases (₹75,000 captured / ₹88,000 paid), zero-capture payments, reconciliation after later purchase capture, profile isolation, validation, unchanged expense counts/calendar spending and distinct dated Activity. `credit-card-payments.spec.js` verifies desktop/mobile settlement, retained spending, reducing unpaid overview, errors/retries, desktop/mobile unmatched-payment display and additional payments after coverage, and open-statement gating. `MonthlyPaymentOverviewServiceTest` verifies remaining bills without reducing the planned source.

The due-month mapping is deterministic: if the due day is after the statement day, the bill is generated in the due month; otherwise it is generated in the previous month. Its billing period begins on the previous generation day and ends the day before the current generation day. With generation day 1 and due day 21, September purchases are due October 21 and October 1 purchases are due November 21. Date-only purchase recording assigns generation-day purchases to the next cycle. Payment recording opens on the generation date, not on the last included purchase date. V39 preserves existing settlements while shifting their statement-end key back one day; calculation version 13 invalidates old current/next projections. The first-day regression in `MonthlyFinancialSnapshotCreditCardTest` verifies ₹480 on October 1 is excluded from October and projected once for November 21, including refresh of version-12 snapshots and short/leap-month boundaries. This avoids asking the user to manually calculate an ambiguous billing-period range.

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

The `MONTHLY_COMMITMENT` presentation reads these snapshots and is returned by the monthly commitment endpoint. It contains a current-month card followed by a next-month runway card, each with its own evidence list. When the user has shared an exact monthly salary, each of those two cards adds its own deterministic commitment-to-salary percentage and signed remaining amount; salary never becomes a standalone card or exposes the salary amount itself. A shared range is shown only as private qualitative context, without a fabricated percentage. One AI request writes the two cards together from verified facts, so the next card continues the current-card thought without a second AI round-trip; all amounts, payoff facts, and source inclusion remain deterministic. Its evidence lists the source loan/SIP rows, amounts, due dates, and labels. Each row also carries a deterministic, playful status tag: a loan nearing payoff gets its exact end month, a longer loan is marked as a long-game member, and an SIP is a future-you contribution. This keeps the detail sheet useful without adding a second AI request per row. The story does not include stock holdings, cash balances, or ordinary recorded expenses in v1.

For a loan source, the snapshot also stores deterministic payoff facts: `remainingPayments`, `endsInMonth`, and `freesFromMonth`. The AI copy generator receives only these verified facts plus bucket totals; it uses an approved Monthly Commitment prompt for a warm closed-circle voice (for example, “leaving the chat” or “escape artist”), with at most one emoji. It may not calculate, alter, or invent payoff information, and it must never joke about debt stress, missed payments, low balances, or the user.

#### Population and refresh rules

1. **Existing users:** the first monthly Stories read finds no current-month snapshot, builds it from the user’s existing source records, saves it, and returns it. No re-entry is required.
2. **New or changed sources:** loan create/update and mutual-fund SIP create/confirmation call `MonthlyFinancialSnapshotService.refreshCurrent(user)` inside the same transaction. Source and snapshot therefore commit or roll back together.
3. **Normal reads:** once present, the Stories response reads the stored snapshot rather than scanning loans and investments.
4. **Future source mutations:** any new command that changes a commitment source—loan closure, SIP pause/resume/edit, a user-managed recurring commitment, emergency reserve rule, or goal contribution—must invoke the same refresh method in its transaction.
5. **Future buckets:** Essential living, emergency reserve, goal contributions, and protection commitments extend this one payload; they must not create independent commitment tables or duplicate total-calculation logic.

## FIN-024 — Read-only cross-module money chat

**Status:** Implemented prototype · **Priority:** P1

The V1 chat uses the canonical monthly commitment calculator for current/next-month intended amounts. It exposes owned loan, mutual-fund, stock and credit-card records/history through bounded read-only tools; it never uses a module list method that creates scheduled rows. It does not fetch live prices for chat or treat holdings as spendable cash. The salary profile remains masked in portal responses; the monthly-plan tool supplies only a backend-calculated difference when an exact regular monthly estimate exists. An exact salary can be inferred mathematically from the total and difference, so this derived disclosure is disclosed in the chat contract.

Given a next-month salary shortfall question, the assistant reads the canonical projection, separates debt/card obligations, planned investing, essential living and earmarked savings, then may compute a hypothetical reduction to selected non-debt sources. The result states original total, revised total, remaining gap and what assumption each reduction requires. No scenario saves a user record. Loan EMIs and credit-card bills cannot be reduced by the scenario tool; the user would need a verified lender/card arrangement outside this chat. A SIP reduction is conditional on provider terms and savings-goal trade-offs, not an automatic instruction to skip a payment.

Acceptance coverage: private exact salary yields numeric difference, range/missing/irregular salary does not; all owners and histories remain isolated; no live valuation or available-cash claim; protected sources remain unchanged in scenarios; planned investing and savings are counted once in the snapshot.

The main dashboard owns the Money dialog: Home → Your money opens the existing source forms, and closing returns to the dashboard. `frontend/e2e/expense-capture.spec.js` verifies opening and closing on desktop and phone.

### Skipped occurrence consistency (FIN-018)

A skipped loan EMI, recurring bill, mutual-fund SIP, or ETF monthly purchase is excluded from its scheduled month’s monthly-plan buckets, evidence, full intended commitment, and salary allocation/shortfall. The calendar overview and View monthly plan use the same inclusion rule. Completed payments remain planned and contribute to progress; future months keep their scheduled sources. Existing cached snapshots are recalculated on the calculation-version change. Regression coverage checks skipped versus confirmed investment occurrences and preserves next-month sources.

FIN-023 calendar acceptance: Active stock/ETF monthly plans appear on their investment day in Home calendar and its due Activity list. Review opens the owning stock details; confirming or skipping removes the pending row. Home loads stocks on startup so this works before opening Your money.

Still-to-pay acceptance: a pending ₹10,000 stock/ETF purchase adds ₹10,000 to Still to pay and its included investing subtotal. Confirming or skipping that occurrence removes its planned amount from both figures. Pending mutual-fund SIPs follow the same rule. Completed payments and skipped occurrences are excluded by status, and earmarked savings remain separate.

## Configurable advance payment window

Pay and Skip for recurring commitments (including daily/weekly dates), loan EMIs, mutual-fund SIPs, and stock monthly plans open five days before the scheduled due date by default, clamped to the first day of that same calendar month. `PAYMENT_ADVANCE_DAYS` configures `app.payments.advance-days`; zero restores due-day eligibility. Occurrence responses expose `actionAvailable` separately from `status`. In the advance window they remain `UPCOMING` (commitments/EMIs) or `SCHEDULED` (SIPs/stock plans), with Pay and Skip enabled and neutral styling. `DUE`, red borders and “Due now” begin only on the scheduled due date and remain for unpaid overdue occurrences. Monthly Commitment evidence, Money details and Home Activity use status for reminders and `actionAvailable` for decisions; early actionable Activity rows remain neutral. Saved completed/skipped outcomes remain final; scheduled due dates and planning amounts are unchanged. Actual payment dates remain the dates the user records.

Acceptance coverage: due October 15 is disabled October 9 and enabled October 10; due October 5 is enabled October 1; due October 1 is disabled September 28. The same rule blocks December-to-January early actions and supports configured two-day and zero-day windows. Early eligibility must not show a red due border or “Due now”; the reminder begins on the due date. Covered by `PaymentActionWindowTest`, the source service tests, and the advance-window browser regression.

## FIN-025 — Unified accounts for planning context

**Status:** In Progress · **Priority:** P1

1. Your money has one Accounts section with multiple bank/debit and credit-card accounts. Captured references and existing cards retain their IDs and expense links. New accounts are immediately available in expense options; captured names can be assigned a type.
2. Bank/debit setup requires only a name and type. Do not collect opening balances, actual receipts or payment funding sources, compute remaining bank money, or show account balance/spending summaries. Account setup supplies context, not a ledger.
3. Credit-card setup requires issuer and bill generation/due days. Purchases stay expenses in their purchase month; FIN-022 separately records settlement against the captured bill without updating a bank balance or duplicating spending.
4. Optional salary range/exact/skip is a visible income-context card inside Accounts, without a collapsed disclosure. Setup and masked saved-summary/edit remain directly available. All three choices persist through the existing private income API, providing context for planning and AI insights. No actual deposit is inferred or requested.
5. Reads/writes are scoped to the active real/demo profile. Foreign/inactive references, invalid names/types and card cycle details are rejected. Configured type changes and generic merges are rejected to protect identity and billing history; aliases remain usable. Legacy card APIs reject bank references.
6. Forms work on phone widths and disable writes offline. Account APIs expose identity/type/card-cycle metadata only. Existing unused V40 receipt/balance storage is preserved for migration compatibility and does not participate in these flows.

Acceptance coverage: `WebAccountsPostgresTest` verifies simple setup, identity reuse, ownership, card configuration and no ledger response/writes. `accounts.spec.js` verifies multiple types on desktop/mobile, optional salary ranges and no balance/receipt controls.

### FIN-022/FIN-025 — Real May-to-June card journey

[Browser specification](../../../frontend/e2e/features/credit-card-monthly-journey.feature) and [real-API Playwright test](../../../frontend/e2e/credit-card-monthly-journey.spec.js) create bank and credit-card accounts through the unified UI and record four May expenses, then advance backend/browser clocks to June. May card spending is ₹4,000 within ₹4,600 total spending. Generation day 1/due day 21 produces June's bill from May 1–31; settlement remains disabled before generation. A June 1 purchase of ₹900 belongs to July 21. Recording June payments of ₹1,000 and ₹3,000 clears the bill while June spending stays ₹900. Reload verifies history, unchanged May spending and July's projected bill. The journey uses real authenticated API reads and manual capture, with no mocked financial responses or bank ledger.

### Credit-card payment visualization (FIN-022)

Accounts presents each captured bill with a chronological purchase-period → generation-date → due-date timeline and separate captured/projected, paid and still-due amounts. Closed statements show an accessible payment progress bar: no payment is 0%, a ₹2,000 payment against ₹5,000 is 40%, and full settlement is 100%. Open statements show an amount-building explanation without a payment-progress bar; payment stays disabled. The timeline becomes vertical on phones. Dated payment history and explicit payment recording remain available. These visuals describe recorded purchases and settlement, never an available bank balance or verified issuer statement.

Coverage: `accounts.spec.js` saves range, exact and skipped income from visible Accounts setup and checks masked edit access. `credit-card-payments.spec.js` verifies timeline, 0/40/100% progress, projected-state behavior and phone layout.

Each bill also compares selected-calendar-month purchases for that card with the bill amount due in the same month. They use separate bars and explicit labels, so a June ₹900 purchase can appear beside the ₹4,000 June bill from May without counting ₹4,900 as June spending. A card with purchases but no selected-month bill still appears. Coverage asserts API month/statement separation and browser comparison labels. Income remains one optional profile-wide estimate inside Accounts.

Credit-card start-month acceptance (FIN-022/FIN-025): optional `YYYY-MM` specifies the first due month included in bill lists and canonical monthly plans. Earlier bills cannot be paid in the app; purchases retain their original spending dates. The first included bill uses the full preceding statement period. Existing cards with no supplied start month retain historical coverage. Invalid months fail validation; a start-month change after settlements is rejected to preserve history. V41 stores the fact and calculation version 14 rebuilds cached plans. Coverage: snapshot unit test, `CreditCardBillPostgresTest`, account browser setup and the real May→June journey starting bills in June.

AI context acceptance: read-only account records expose configured type alongside owned names; credit-card records expose the saved start month alongside issuer and cycle days. The canonical plan honors the start month. `ExpenseQueryPostgresTest` verifies these facts reach the tool without bank-balance fields. Salary range/exact/skip continues through the existing private income and monthly-plan tools.

Home simplicity acceptance: Home shows a compact selected-month card-bill remaining-total widget with View credit-card bills only while at least one bill has a positive remaining amount (or loading failed, to allow retry). Once all captured bills are covered, hide the widget, including after reload; current-month purchases alone do not keep it visible. Settled details, unmatched payments and history remain accessible through Your money → Accounts. It has no card timeline, comparison bars, payment progress, history or payment form entry. The link opens Your money → Accounts and focuses its selected-month Credit-card bills section, which owns detailed visuals, recording and history. Payment completion refreshes the Home total and monthly plan. Closing Accounts returns to Home. Desktop/mobile settlement tests and the real May→June journey verify this handoff.

Accounts presentation acceptance (FIN-025): account setup, income context and card-bill details follow the existing Money-module typography: DM Sans body and controls, Manrope headings/amounts, compact 19px section/15px subsection/13px card-title scale, 11px body and 9–10px supporting labels. Shared ink/muted/green/line/paper tokens apply throughout. Desktop/mobile account tests compare the inherited body/control fonts and heading family with existing Money modules.

FIN-019/FIN-025 combined-income acceptance: Accounts presents Monthly income, with optional range, exact total or skip. The question asks for approximate total monthly income across all sources; no per-source entries or receipt-frequency question are shown. Portal writes normalize this monthly estimate to MONTHLY. Saved legacy salary preferences remain usable and masked; existing API/column names remain compatible. Commitment labels and AI tool descriptions refer to income. Account browser tests verify all three choices and monthly submission; commitment tests verify income percentage/difference labels.


Unified credit-card presentation acceptance (FIN-022/FIN-025): Each credit-card account contains its selected-month bill comparison, timeline, progress, payment recording and history in the same account card as configuration and billing days. Match account `cardId` to bill `cardId`, never display names; legacy card names may differ from the expense-account name. Bank/debit cards have no bill controls. A card without an included bill shows the selected-month empty state. Loading/errors allow retry within the card. Bills whose account metadata is unavailable remain accessible in an Accounts fallback section. Home's bill link focuses the first bill region within Accounts. Desktop/mobile browser coverage verifies distinct names and multiple cards do not mix payments or duplicate bill panels.

### FIN-024 — Alignment with current Accounts and payment logic

Read-only chat now exposes `read_credit_card_bills` with due-month selection and bounded server pagination, using the same Accounts bill calculator without unbounded payment-history loading. It preserves card start-month inclusion, generation-day boundaries, calendar-month purchases, recorded settlement totals, floored remaining and unmatched payment amounts. Paginated credit-card history exposes dated settlements with ownership joins. Card records include account-reference IDs for identity-based joins. No bank-balance/receipt migration storage is exposed; current Accounts has no such flow.

Monthly-plan evidence adds the canonical Home unpaid overview separately from intended amounts, and canonical calculation detail/payoff facts per source. Pending investing is included in still-to-pay; savings remains separate. Simulations omit unpaid overview fields. Monthly income remains a masked combined estimate. Users review or record card payments within Home → Your money → Accounts, and savings within the owning commitment details.

Acceptance coverage: `CreditCardBillsToolTest`, `MonthlyPlanningToolTest`, `ExpenseQueryPostgresTest` and `CreditCardBillPostgresTest` verify bounded dispatch, due-month arithmetic, identity/ownership, dated history and separation of spending, settlement and unpaid/intended totals.
