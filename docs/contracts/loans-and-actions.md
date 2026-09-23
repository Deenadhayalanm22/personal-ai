# Loans and actions

Loans are user-entered planning records, separate from conversational expense capture. Actions are server-generated follow-ups; the currently implemented type is `LOAN_CLOSURE_CONFIRMATION`. `Home.svelte` loads both only when the user opens **Your money**.

## Loans

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/loans` | `{ loans }`, newest first. | Money view loan list. |
| `GET /api/web/loans/{id}/history` | Owned loan's `{ id, loanName, history }`; history contains every scheduled EMI with status, due date, planned amount, and paid facts when recorded. The View details client renders only the next payable occurrence for an active loan; the Closed loans history view renders all occurrences. | Shared View details sheet. |
| `POST /api/web/loans` | `{ loanName, loanType, lenderName, originalPrincipal, monthlyEmiAmount, totalTenureMonths, firstEmiDueDate, status?, notes? }`; returns loan with `201`; missing status means `ACTIVE`. | Add-loan form. |
| `PATCH /api/web/loans/{id}` | One or more mutable create fields except status; returns updated loan. Before any `PAID`, `SKIPPED`, or pre-closure outcome, all fields are mutable. Afterwards, only loan name, lender, and notes are mutable; original principal, EMI, tenure, and first due date are rejected as a schedule change. The edit control is in the top-right of the owned loan's View details sheet, not its compact card. | Edit-loan form. |
| `DELETE /api/web/loans/{id}` | Deletes the authenticated user's loan and its EMI occurrences; returns `204`. Any associated open loan action is removed and current commitment snapshots are refreshed. Deletion is initiated from the top-right of View details; a deleted loan is not a closed loan and is never listed in Closed loans. | View details delete control. |
| `POST /api/web/loans/{id}/emi-occurrences/{month}/paid` | Marks the owned `YYYY-MM` occurrence paid using the server date and EMI amount; returns the loan with occurrences. A paid occurrence returns `409 EMI_ALREADY_PAID`. | Due-EMI control. |
| `POST /api/web/loans/{id}/emi-occurrences/{month}/skipped` | Marks the owned due `YYYY-MM` occurrence skipped. Request: `{ bankPenaltyAmount? }`, where a supplied amount is positive. It removes that month's loan amount from the current commitment story and extends the scheduled tenure by one month; returns the loan with occurrences. | Due-EMI Skip confirmation. |
| `POST /api/web/loans/{id}/pre-close` | Immediately closes the owned active loan. Request: `{ settlementAmount }`, which must be positive. It records the settlement, removes all future scheduled EMI occurrences and commitment entries, and returns the closed loan. | Pre-close confirmation. |
| `POST /api/web/loans/{id}/restructures` | Rebuilds the owned active loan's future unpaid schedule without changing historical outcomes. Request: `{ effectiveMonth, monthlyEmiAmount, remainingTenureMonths }`; all values are required and positive, and `effectiveMonth` must be a future unpaid scheduled month. It records the revision, refreshes commitments, and returns the same loan with its revised occurrences. | Restructure remaining loan flow. |

`loanType`: `HOME`, `VEHICLE`, `PERSONAL`, `EDUCATION`, `CREDIT_CARD_EMI`, `OTHER`. `status`: `ACTIVE` or `CLOSED`. Each response also contains `hasRecordedOutcome` and `restructuredFrom` for edit locking and schedule-revision display, plus server-calculated `completedEmiCount` and `remainingEmiCount`. Completed counts only `PAID` EMI occurrences, including seeded historical payments; a due or skipped month does not advance the progress bar. Remaining is tenure minus completed. The active profile timezone and application clock determine due status; the frontend renders server counts rather than calculating progress from its device clock. Amounts and tenure must be positive; name/lender are required and max 255 characters. Failures: `400 INVALID_LOAN`, `404 LOAN_NOT_FOUND`. Delete never exposes or removes another user's loan.

Monthly EMI occurrences are separate from the loan definition. A due occurrence has its own `UPCOMING`, `DUE`, `PAID`, or `SKIPPED` state, planned amount, due date, its original planned amount, optional paid amount/date, optional bank penalty, and a pre-closure settlement marker. The details sheet exposes only the next payable occurrence; its Pay and Skip controls are disabled until its local due date. Marking an occurrence paid updates only that month and refreshes the Monthly Commitment progress. Skipping a due occurrence optionally records its bank penalty amount, removes its amount from that month's commitment story, and appends one scheduled EMI month to the loan. A loan with no recorded EMI outcome may have its original schedule edited. Once it has a `PAID`, `SKIPPED`, or pre-closure outcome, a restructure records a revision and replaces only future unpaid occurrences; the original loan remains one card and history keeps both prior outcomes and the revision entry. Pre-closing an active loan records the user-entered settlement and immediately closes it; its Closed loans history shows only actual payments and that settlement, never future scheduled EMI rows. Marking the final scheduled EMI paid sets the loan to `CLOSED`, removes any open closure action, and prevents later-month commitments. A month outside the loan's tenure is rejected with `400 INVALID_LOAN`.

## Actions

| Endpoint | Contract | Frontend owner |
| --- | --- | --- |
| `GET /api/web/actions` | `{ actions }` for open actions; each is `{ id, actionType, referenceType, referenceId, title, description, scheduledCompletionDate }`. | Money view actions. |
| `POST /api/web/actions/{id}/complete` | Marks owned open action complete and returns it. | Completion control, then reloads actions and loans. |

Actions are domain workflows, not general-purpose user tasks. A due `LOAN_CLOSURE_CONFIRMATION` is surfaced only as `Review final EMI` in the Monthly Commitment story, never as a separate home-screen action queue. It opens and highlights the owned loan; the user must explicitly choose `Mark final EMI paid` before the existing completion endpoint closes it. This confirms the final EMI only and is not a monthly payment ledger.

## Private income outlook

This is a voluntary planning input. It is never treated as a bank balance, an income transaction, or evidence for a spending story. The salary prompt begins with a range; exact salary is an optional alternative.

| Endpoint | Contract |
| --- | --- |
| `GET /api/web/income-outlook` | `{ salary }`; `salary` is empty when no salary is shared, otherwise it is `{ maskedValue: "**" }`. It never returns the saved range, exact amount, or frequency. |
| `PUT /api/web/income-outlook/salary` | Request: `{ salaryVisibility: RANGE|EXACT|SKIPPED, salaryRange?, exactMonthlySalary?, salaryFrequency }`. A range is required for `RANGE`, and a positive amount for `EXACT`. A shared-salary response is `{ maskedValue: "**" }`; a skipped response is empty. Neither echoes submitted salary data. |

Ranges are `UNDER_25000`, `FROM_25000_TO_50000`, `FROM_50000_TO_100000`, `FROM_100000_TO_200000`, or `OVER_200000`. Validation failure is `400 INVALID_INCOME_OUTLOOK`.
