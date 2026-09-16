# Recurring commitments

Recurring commitments are user-managed planning rules for repeatable obligations. They are not transactions, proof of payment, or budgets. The user explicitly creates or updates them; the server refreshes current and next monthly commitment snapshots in the same write.

| Endpoint | Contract |
| --- | --- |
| `GET /api/web/recurring-commitments` | Returns `{ items }`. |
| `POST /api/web/recurring-commitments` | Creates and returns a commitment with `201`. |
| `PATCH /api/web/recurring-commitments/{id}` | Updates supplied fields and returns the commitment. |
| `GET /api/web/recurring-commitments/review?month=YYYY-MM` | Returns unresolved category/subcategory commitment candidates and eligible user-owned choices. |
| `POST /api/web/recurring-commitments/review/{transactionId}` | Body `{ commitmentId }` links the candidate; `{ commitmentId: null }` records `NOT_LINKED`. Returns `204`. |

Request fields are `label`, `amountMode` (`FIXED`, `RECENT_BILL_ESTIMATE`, `USER_ESTIMATE`), `planningAmount`, optional `dueDay` (1–28), optional `effectiveMonth` (`YYYY-MM`), optional `status` (`ACTIVE`, `PAUSED`, `ENDED`), optional category/subcategory, and either `sourceTransactionId` or explicit `transactionIds`. Linked transaction IDs must be visible and owned by the active profile. `RECENT_BILL_ESTIMATE` requires at least two explicitly linked transactions. Responses return the saved fields plus `id` and `transactionIds`.

Capture never asks a matching follow-up. An exact category/subcategory plus commitment merchant match becomes `MATCHED`; another category/subcategory match becomes `CANDIDATE`; all other transactions are `NOT_LINKED`. Only candidates appear in the Monthly Commitment story's `OPEN_COMMITMENT_REVIEW` sheet.
