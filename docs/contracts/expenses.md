# Expenses and calendar

Expenses originate from conversational capture, normally WhatsApp, and appear as records users can correct or soft-delete. Dashboard values use the active profile's timezone and currency. Editing or deleting an expense marks affected money stories for recalculation.

## Read contracts

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/expenses/calendar?month=YYYY-MM` | Required month. Returns `{ month, currency, timezone, recordedDays, transactionCount, totalSpend, highestSpend, intensityMethod, days }`; a day is `{ date, transactionCount, totalSpend, intensity }`. Invalid month: `400 INVALID_MONTH`. | `App.svelte` loads; `Home.svelte` renders month grid. |
| `GET /api/web/expenses` | Optional `month` (defaults to user current month), `date`, `category`, `subcategory`, `beforeId`, `limit` (default 20). Returns `{ items, nextBeforeId, filterSummary }`. An item is `{ id, originalMessage, amount, currency, transactionTime, category, subcategory, merchantId, merchant, accountId, sourceAccount }`. | App requests five recent items; Home requests up to 50 for a calendar day. |
| `GET /api/web/expenses/options` | Returns `{ categories, merchants, accounts }`; categories are `{ name, subcategories }`, references `{ id, name }`. | Home edit-expense dialog. |
| `GET /api/web/expense-taxonomy` | Full configured taxonomy. | Implemented, no current UI caller. |

`filterSummary` contains `transactionCount`, `totalAmount`, `currency`, `category`, `subcategory`, `tagIds`, and `tagMatch`. The UI currently exposes neither category filters nor cursor pagination.

## Mutation contracts

| Endpoint | Request and behavior | Frontend owner |
| --- | --- | --- |
| `PATCH /api/web/expenses/{id}` | Send at least one of `{ amount, transactionDate, category, subcategory, merchantId, accountId }`. Amount must be positive; category/subcategory must be a valid pair; reference IDs must be active and user-owned. Returns updated item. | Home edit dialog, then refreshes calendar, recent items, stories. |
| `DELETE /api/web/expenses/{id}` | Soft-deletes an owned visible expense. Returns `204`; inaccessible/missing is `404 EXPENSE_NOT_FOUND`. | Home delete confirmation, then refreshes the three sections. |
| `POST /api/web/expenses/calendar/context` | Body `{ "type": "MISSING_TRANSACTION_DATE", "date": "YYYY-MM-DD", "timezone": "IANA zone" }`. Date cannot be future. Returns `{ contextId, status: "ACTIVE", date, expiresAt, whatsappUrl? }`. | Home calls it for an empty past day and shows WhatsApp handoff. |

The context expires after `app.web.action-context-expiry` (30 minutes by default), replaces another active context, and associates the next WhatsApp message with its date unless that message explicitly names a date. `whatsappUrl` is omitted if no WhatsApp destination is configured.
