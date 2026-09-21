# FIN-EPIC-006 — User-managed recurring commitments

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | Proposed |
| Goal | Let a user deliberately turn repeatable household bills and personal obligations into explainable monthly planning commitments without guessing from spending history. |

## Business value

The Monthly Commitment story currently explains planned loan EMIs and SIPs, but it omits ordinary obligations such as rent, electricity, internet, insurance, and family support. A user should be able to include the obligations they expect to pay each month, see a realistic current- and next-month total, and retain control when an amount changes, varies, or is absent in one month.

This feature is planning data. It is not a bank balance, a payment mandate, an automatic bill detector, or proof that a bill has been paid.

## Product boundary

1. A **commitment** is a user-managed recurring item, such as rent, electricity, internet, insurance, subscriptions, or family support. It may originate from any captured expense or be created manually.
2. A **budget** is a future category spending limit, such as groceries, eating out, shopping, or transport. Budgets are out of scope and must not be included in the commitment total merely because spending is repeatable.
3. The first release does not automatically classify or add a transaction as recurring. A transaction can prefill a new commitment, but user confirmation is always required.
4. A recorded transaction is an actual past expense. A commitment amount is a future planning projection. They are displayed together for context but are never added together as two expenses.

## FIN-020 — Review and manage recurring commitments

**Status:** Proposed · **Priority:** P1

### Entry points and interaction

1. **Monthly Commitment story:** the evidence/detail view exposes `Manage commitments`. It is the central place to add, edit, pause, end, and review commitment projections.
2. **Expense transaction:** a transaction detail or edit affordance exposes `Add as monthly commitment`. It opens the same commitment form with its label, category, merchant/reference, amount, and date prefilled; saving does not modify the transaction.
3. **Manual add:** Manage commitments exposes `Add commitment` so a user can plan for an obligation with no suitable transaction in the selected month.
4. **Month review:** Manage commitments can show a selected completed month's transactions with an `Add as monthly commitment` action. It is available whenever the user chooses; it is not a blocking or end-of-month-only prompt.

### Acceptance criteria

1. **Given** an owned visible expense or the manual-add action, **when** the user creates a valid commitment, **then** it is persisted only for that user with a label, monthly recurrence, active status, and a projection method; a source transaction/reference/category is optional display and evidence context.
2. **Given** a user selects `Add as monthly commitment` from an expense, **when** the form opens, **then** its fields are prefilled from that expense and the user can change them before saving; the original transaction remains unchanged.
3. **Given** a user visits Manage commitments, **when** they choose a completed month, **then** they can review only their own visible transactions for that month and add any one as a commitment. No transaction is automatically marked as a commitment or excluded from a future budget feature.
4. **Given** an active commitment, **when** the user edits its label, due day, projection method, category/reference context, or future default amount, **then** the change affects the selected effective month and later months only; previously stored snapshot projections remain historically explainable.
5. **Given** an active commitment, **when** the user pauses it, ends it, skips a selected month, or supplies a one-month amount override, **then** the action has the stated scope and is visible in Manage commitments and story evidence. `Skip this month` does not end the commitment; `Pause` excludes it until resumed; `End` excludes it from its end month onward.
6. **Given** another user's commitment, source transaction, or reference ID, **when** it is read or mutated, **then** it is not visible or changed and the API returns the appropriate ownership-safe `4xx` response.
7. **Given** a user created a commitment by mistake, **when** they select Delete from Manage commitments, **then** the rule is removed, current and next projections refresh, and any transaction match is unlinked without deleting the transaction or rewriting historical snapshots.
8. **Given** an active commitment with a due day reaches that day, **when** the Monthly Commitment evidence is opened, **then** it is highlighted as due and Review opens its Manage commitments row. The user can mark the current occurrence done; this acknowledgement leaves the planned total unchanged and removes the due highlight without applying a separate completion colour.
9. **Given** a user creates a commitment after its configured due day in the current month, **when** they open Monthly Commitment, **then** it remains a planning projection but is never shown as a retroactive current-month due reminder. Its first due reminder is on that due day in the next month.
10. **Given** a commitment occurrence was marked done, **when** the user selects `View details`, **then** the existing details-sheet presentation lists the completed payment with its due and completion dates.
11. **Given** a user has a flexible commitment such as bike service, **when** they complete it before its expected date, **then** they can record the actual amount and completion date and explicitly set the next expected date without changing the planning estimate or usual recurrence.
12. **Given** a user has a flexible commitment such as an internet recharge that is usually every three months, **when** an offer leads them to recharge after two months, **then** they can record that actual payment and choose the next expected date; the usual recurrence remains available as a future suggestion rather than being silently overwritten.

### Commitment projection choices

The creation/edit form makes the projection basis explicit:

| Choice | Intended use | Projected amount |
| --- | --- | --- |
| Fixed amount | Rent, internet, subscriptions, stable support | User-supplied amount effective from a selected month. |
| Recent-bill estimate | Electricity, water, variable utility | Calculated from explicitly linked prior paid transactions when enough history exists. |
| User estimate | Variable expense where history is absent or not representative | User-supplied planning amount, which remains unchanged until edited. |

`Recent-bill estimate` is offered only after at least two linked historical transactions. The form shows the payment count, values, selected history window, and calculated estimate before the user saves it. The implementation may use a robust median rather than a literal arithmetic mean to prevent one unusually high bill from distorting the plan; UI wording must say `recent-bill estimate` and expose the values used. The user can always keep a previous estimate or enter their own amount.

When a new matching payment is recorded, the product may show a non-blocking suggestion to use the newly calculated estimate going forward. It must never silently replace a user-selected fixed amount or estimate.

### Planned flexible recurrence

Commitments are renamed from **Monthly commitments** to **Commitments**. The standard recurrence picker offers daily, weekly, monthly, yearly, and a custom `Every…` interval. A monthly projection must include an occurrence only in the month it is expected; it must not annualise a non-monthly payment into an invented monthly expense.

A flexible commitment retains a usual recurrence (for example, `every 4 months` for bike service or `usually every 3 months` for an internet recharge) and has a user-controlled next expected date. Completing it records the actual paid amount and completion date separately from its planning estimate. The completion flow then offers the usual recurrence as a next-date suggestion and also lets the user choose another date or leave it unscheduled. This preserves truthful payment history while allowing offers and usage-based maintenance to change the immediate plan.

### Integration-test scenarios

- Create a fixed Rent commitment from an owned expense; assert prefilled fields, unchanged transaction, ownership, and current/next projection.
- Create an Electricity commitment using three explicitly linked payments; assert the returned candidate basis/amount and user confirmation before it contributes to a snapshot.
- Change Rent from ₹15,000 effective October to ₹16,000; assert September retains ₹15,000 and October/current-or-next projections use ₹16,000 as appropriate.
- Skip Electricity for one month, then assert it is excluded only from that month's projection and returns in the following month.
- Attempt create/read/update against another user's transaction or commitment; assert no disclosure or change.
- Edit a fixed commitment from ₹15,000 to ₹16,000 and delete it; assert the edited projection refreshes, deletion removes it from Manage commitments and the current story, and its original transaction remains.
- Advance the clock past an active commitment’s due day; assert the story shows it as due, Review focuses the commitment, and marking it done refreshes the same occurrence as completed while preserving its planned amount.
- On 15 April, add commitments due on the 1st, 10th, and 25th; assert none are retroactively due. Advance to the 25th and complete only that occurrence, then to 30 April and assert no due reminder remains. Advance to 1 May and assert only the first-day commitment is due and can be completed.
- Browser regression: mark a due commitment done, open View details, and assert the completed occurrence is retained in payment history.
- Browser regression: create a ₹2,000 Bike service commitment every four months; complete it early for ₹2,400, choose a new next expected date, and assert the history retains actual facts while the planning estimate and usual recurrence remain unchanged.
- Browser regression: create a ₹799 Internet recharge commitment usually every three months; complete it after two months for ₹699, choose the new date, and assert the history is updated without silently changing the usual recurrence.

## FIN-021 — Include commitments in Monthly Commitment stories

**Status:** Proposed · **Priority:** P1

### Acceptance criteria

1. **Given** an active user-managed commitment applicable to the current or next month, **when** the Monthly Commitment story is read, **then** its projected amount is included once in the canonical snapshot and story total under `ESSENTIAL_LIVING`.
2. **Given** a skipped, paused, ended, or not-yet-effective commitment, **when** that target month's snapshot is built, **then** it is excluded or uses its applicable monthly override exactly once.
3. **Given** a variable commitment using a recent-bill estimate, **when** its snapshot is built, **then** the snapshot stores the calculated projected amount and explainable basis (history window/payment values or versioned calculation facts), rather than recalculating a past story from later history.
4. **Given** a commitment create, edit, effective-date change, one-month override, skip, pause, resume, end, or confirmed estimate update, **when** it commits, **then** the current and next `monthly_financial_snapshot` rows refresh in the same transaction.
5. **Given** the story evidence is opened, **when** a commitment is present, **then** it shows the label, projected amount, expected due date when known, bucket, and a truthful tag such as `fixed monthly amount`, `recent-bill estimate`, `overridden this month`, or `skipped this month`.
6. **Given** actual expenses have been linked to a commitment, **when** the story is rendered, **then** those expenses can be shown as historical/payment context but are not added to `fullIntendedCommitment` a second time and do not imply a bill was definitely paid.
7. **Given** a captured transaction has the same category/subcategory as one or more active commitments but cannot be safely linked by the saved merchant/reference, **when** the Monthly Commitment story is opened, **then** it opens a story-scoped review sheet with the eligible commitment choices and `This is not a commitment`; capture itself asks no follow-up question.
8. **Given** the user resolves a review item in that sheet, **when** they choose a commitment or reject it, **then** the transaction stores `MATCHED` or `NOT_LINKED` respectively, the item leaves the review sheet, and current/next commitment snapshots refresh.

### Snapshot and story design

`monthly_financial_snapshot` remains the sole canonical per-user/per-month projection. This epic adds an `ESSENTIAL_LIVING` bucket alongside `DEBT_REPAYMENTS` and `PLANNED_INVESTING`; it does not add a competing commitment-total table or calculate totals in the frontend.

For each target month, the deterministic projection order is:

```text
not effective, paused, or ended          → exclude
month-specific skip                      → exclude for that month
month-specific amount override           → use override
otherwise fixed/user estimate/effective amount → use current effective amount
otherwise recent-bill estimate            → use confirmed history-based estimate
```

The Monthly Commitment story continues to render server-provided amounts only. Its current-month and next-month cards can include a third breakdown line, `Essential living`, and evidence rows for each included source. AI copy may describe only verified snapshot facts; it must not infer that a variable bill will arrive or that an obligation has been paid.

### Integration-test scenarios

- Seed loans, SIPs, and two active commitments; assert the snapshot contains all three semantic buckets, correct total, evidence rows, and current/next-month runway amounts.
- Apply an override and a skip to different months; assert story totals/evidence are deterministic and snapshots are refreshed within the same committed write.
- Record a later unusually high utility payment; assert an earlier stored snapshot remains unchanged and a newer estimate is only used after the user confirms it.
