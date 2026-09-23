# Recurring commitments

Recurring commitments are user-managed planning rules for repeatable obligations. They are not transactions, proof of payment, or budgets. The user explicitly creates or updates them; the server refreshes current and next monthly commitment snapshots in the same write.

| Endpoint | Contract |
| --- | --- |
| `GET /api/web/recurring-commitments` | Returns `{ items }`. |
| `GET /api/web/recurring-commitments/{id}/history` | Returns the owned commitment's `{ id, label, planningAmount, history }`; history contains completed occurrences with due and completion dates. |
| `POST /api/web/recurring-commitments` | Creates and returns a commitment with `201`. |
| `PATCH /api/web/recurring-commitments/{id}` | Updates supplied fields and returns the commitment. |
| `DELETE /api/web/recurring-commitments/{id}` | Deletes an owned commitment and returns `204`. Any transaction match to it is unlinked; past stored snapshots remain unchanged. |
| `POST /api/web/recurring-commitments/{id}/occurrences/{YYYY-MM}/done` | Legacy acknowledgement for an owned, due current-month occurrence. |
| `POST /api/web/recurring-commitments/{id}/occurrences/complete` | Records `{ actualAmount, completedAt, nextExpectedDate }` for an owned active commitment. The actual payment is history; it never replaces the planning estimate. |
| `POST /api/web/recurring-commitments/{id}/occurrences/{YYYY-MM}/skip` | Marks the owned active current-month occurrence `SKIPPED`; a completed occurrence cannot be skipped. |
| `POST /api/web/recurring-commitments/{id}/occurrences/{YYYY-MM}/extra` | Body `{ amount, reason }` adds a positive extra amount with a required reason of 1–200 characters to an owned completed occurrence. It does not change the usual planning amount. |
| `GET /api/web/recurring-commitments/review?month=YYYY-MM` | Returns unresolved category/subcategory commitment candidates and eligible user-owned choices. |
| `POST /api/web/recurring-commitments/review/{transactionId}` | Body `{ commitmentId }` links the candidate; `{ commitmentId: null }` records `NOT_LINKED`. Returns `204`. |

Request fields are `label`, `amountMode` (`FIXED`, `RECENT_BILL_ESTIMATE`, `USER_ESTIMATE`), `planningAmount`, `recurrenceUnit` (`DAY`, `WEEK`, `MONTH`, `YEAR`), `recurrenceInterval` (positive integer), `nextExpectedDate` (`YYYY-MM-DD`), optional `flexibleSchedule`, optional `dueDay` (1–28), optional `effectiveMonth` (`YYYY-MM`), optional `status` (`ACTIVE`, `PAUSED`, `ENDED`), optional category/subcategory, and either `sourceTransactionId` or explicit `transactionIds`. Linked transaction IDs must be visible and owned by the active profile. `RECENT_BILL_ESTIMATE` requires at least two explicitly linked transactions. Responses return the saved fields plus `id`, `transactionIds`, recurrence fields, and current occurrence.

Only the next expected occurrence contributes to a monthly snapshot; an every-four-month bike service is included in its expected month, not fabricated as a monthly expense. Completing an occurrence records its actual amount and lets the user set the next expected date. A flexible schedule retains its usual cadence as a suggestion without overwriting it after an early payment.

Occurrence responses and history include `status` (`DUE`, `UPCOMING`, `COMPLETED`, or `SKIPPED`), `actualAmount`, and `extraAmount`. History includes skipped and completed rows, plus `extras` entries with each extra `amount` and `reason`. Extra amounts are actual payment context and do not increase the recurring planning estimate.

Capture never asks a matching follow-up. An exact category/subcategory plus commitment merchant match becomes `MATCHED`; another category/subcategory match becomes `CANDIDATE`; all other transactions are `NOT_LINKED`. Only candidates appear in the Monthly Commitment story's `OPEN_COMMITMENT_REVIEW` sheet.

A commitment created after its configured due day is not retroactively due for that month. It remains in the planning projection, but its first due reminder is in the following month; the user cannot mark that past current-month occurrence done.
