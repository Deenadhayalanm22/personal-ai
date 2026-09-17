# Credit-card billing profiles

Credit cards reuse the `ACCOUNT` reference created when a normal expense says it was paid by that card. A card profile configures its billing cycle; it never imports a bank statement, creates a payment, or changes the captured expenses.

| Endpoint | Contract |
| --- | --- |
| `GET /api/web/credit-cards` | Returns `{ cards }` for the active profile. |
| `POST /api/web/credit-cards` | Creates a profile and returns it with `201`. |
| `PATCH /api/web/credit-cards/{id}` | Updates supplied configuration and returns it. |

The request is `{ accountReferenceId, cardName, issuerName, statementDay, dueDay, active? }`. `accountReferenceId` must be an active owned `ACCOUNT`; names are 1–120 characters and both days are 1–28. Responses add `id` and `accountName`. Errors are `400 INVALID_CREDIT_CARD` or `404 CREDIT_CARD_NOT_FOUND`.

For a snapshot month, the amount is the sum of visible transactions recorded against the linked account in the statement period preceding its due date. If `dueDay > statementDay`, that statement closes in the snapshot month; otherwise it closes in the preceding month. The period starts the day after the prior statement day and ends on the closing day, inclusive.
