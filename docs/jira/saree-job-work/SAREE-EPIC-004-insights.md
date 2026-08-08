# SAREE-EPIC-004 — Operational insights and exception control

| Field | Value |
|---|---|
| Parent | INIT-003 |
| Status | To Do |

## SAREE-014 — Show material custody and stock

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. The owner can ask how much thread is with each employee, in owner stock, returned, or recorded as scrap by material/lot.
2. Results distinguish ledger-expected and physically reconciled quantities and disclose unknown opening stock.
3. Every quantity retains its unit; no metre, kilogram, gram, or piece is combined incorrectly.

## SAREE-015 — Show production and productivity

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. Reports show surrendered, accepted, rejected/rework, and cumulative pieces by employee, batch, and week.
2. Weekly output below 20 or above 30 is an exception indicator using the effective standard, not proof of poor work or bad data.
3. Theoretical versus actual yield and available material data are shown with assumptions and unresolved variance.
4. Comparisons do not rank or penalize employees automatically.

## SAREE-016 — Surface actionable exceptions

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. Exceptions include overdue open batch, ambiguous surrender, uninspected pieces, unusual weight, unresolved material variance, and unpaid approved wages.
2. Each alert states the evidence, configured rule, and next safe action.
3. Alerts are owner-configurable, non-duplicative, and never create stock/wage movements by themselves.

## SAREE-017 — Produce a weekly owner summary

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. A conversational summary reports thread issued/returned, pieces received/accepted/rejected, wage earned/paid/outstanding, and notable exceptions for the chosen week.
2. Totals link to underlying employees/batches and disclose unreconciled or incomplete records.
3. Rendering is short and voice-friendly, with drill-down questions offered instead of one dense report.

## SAREE-018 — Add sales only as a separate extension capability

**Status:** To Do · **Priority:** P2

### Acceptance criteria

1. Customer orders, pricing, invoices, and sale/payment collection are not assumed by job-work production.
2. If later enabled, accepted finished stock decreases through explicit sale events and remains traceable to the sales extension.
3. Production wages and customer revenue remain distinct concepts and reports.
