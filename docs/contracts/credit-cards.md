# Credit-card billing profiles

Credit cards reuse the `ACCOUNT` reference created when a normal expense says it was paid by that card. A card profile configures its billing cycle; it never imports a bank statement or changes captured purchases. Bill settlements use separate payment records and never create expense transactions.

| Endpoint | Contract |
| --- | --- |
| `GET /api/web/credit-cards` | Returns `{ cards }` for the active profile. |
| `POST /api/web/credit-cards` | Creates a profile and returns it with `201`. |
| `PATCH /api/web/credit-cards/{id}` | Updates supplied configuration and returns it. |

The request is `{ accountReferenceId, cardName, issuerName, statementDay, dueDay, active? }`. `accountReferenceId` must be an active owned `ACCOUNT`; names are 1–120 characters and both days are 1–28. Responses add `id` and `accountName`. Errors are `400 INVALID_CREDIT_CARD` or `404 CREDIT_CARD_NOT_FOUND`.

For a snapshot month, the amount is the sum of visible transactions recorded against the linked account in the statement period preceding its due date. If `dueDay > statementDay`, that statement closes in the snapshot month; otherwise it closes in the preceding month. The period starts the day after the prior statement day and ends on the closing day, inclusive.

## Bill settlement

- `GET /api/web/credit-card-bills?month=YYYY-MM` (optional; defaults to profile-local current month) returns `{month,bills}` for active owned cards. Each bill is `{cardId,cardName,month,periodStart,statementEnd,dueDate,projectedAmount,paidAmount,remaining,statementClosed,payments}`. Payments contain `{id,paidAt,amount}`, newest first. The amount covers captured purchases only, not an imported or verified issuer statement. `remaining = max(projectedAmount - paidAmount, 0)`; correcting purchases never deletes or changes actual payments.
- `POST /api/web/credit-cards/{id}/payments` accepts `{requestId:UUID,month:"YYYY-MM",amount,paidAt:"YYYY-MM-DD"}` and returns the updated bill. The active real/demo profile is resolved from WEB_SESSION. Foreign/inactive cards return `404 CREDIT_CARD_NOT_FOUND`. Amount must be positive, at most two decimals and 17 integer digits, and cannot exceed remaining captured bill. The statement must be closed and payment date must be on/after its closing date and no later than profile-local today. Invalid payment details return `400 INVALID_CARD_PAYMENT`.
- Full and partial payments are stored against card and statement closing date, with their due month and actual payment date. A card row lock serializes payment writes. Identical retries with the same card/request ID return the existing result; changed details return `409 CARD_PAYMENT_REQUEST_CONFLICT`. Payments never write financial_transaction, affect expense calendars/category totals, or claim to execute a bank payment.
- After a card has recorded payments, changing its account, statement day or due day is rejected with `400 INVALID_CREDIT_CARD`; display names remain editable. This protects the statement identity of saved settlements.
- Home shows selected-month captured/projected bills, paid and remaining amounts, explicit Record bill payment, and dated payment history. Open statements stay projected and cannot receive a settlement. Offline recording is disabled. Activity shows CARD_PAYMENT on its actual date, without expense Edit/Delete controls. V1 has no settlement undo, issuer adjustments, interest/fees, statement import or account balances.
- Purchases remain spending in their purchase month. Planned commitment totals retain the full projected bill; unpaid overview subtracts settlements and floors at zero. Card-bill progress uses recorded payments capped at each captured bill amount. Payments are never added to spending.
