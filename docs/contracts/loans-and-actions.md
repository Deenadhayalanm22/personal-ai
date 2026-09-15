# Loans and actions

Loans are user-entered planning records, separate from conversational expense capture. Actions are server-generated follow-ups; the currently implemented type is `LOAN_CLOSURE_CONFIRMATION`. `Home.svelte` loads both only when the user opens **Your money**.

## Loans

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/loans` | `{ loans }`, newest first. | Money view loan list. |
| `POST /api/web/loans` | `{ loanName, loanType, lenderName, originalPrincipal, monthlyEmiAmount, totalTenureMonths, firstEmiDueDate, status?, notes? }`; returns loan with `201`; missing status means `ACTIVE`. | Add-loan form. |
| `PATCH /api/web/loans/{id}` | One or more mutable create fields except status; returns updated loan. | Edit-loan form. |

`loanType`: `HOME`, `VEHICLE`, `PERSONAL`, `EDUCATION`, `CREDIT_CARD_EMI`, `OTHER`. `status`: `ACTIVE` or `CLOSED`. Amounts and tenure must be positive; name/lender are required and max 255 characters. Failures: `400 INVALID_LOAN`, `404 LOAN_NOT_FOUND`.

## Actions

| Endpoint | Contract | Frontend owner |
| --- | --- | --- |
| `GET /api/web/actions` | `{ actions }` for open actions; each is `{ id, actionType, referenceType, referenceId, title, description, scheduledCompletionDate }`. | Money view actions. |
| `POST /api/web/actions/{id}/complete` | Marks owned open action complete and returns it. | Completion control, then reloads actions and loans. |

Actions are domain workflows, not general-purpose user tasks.
