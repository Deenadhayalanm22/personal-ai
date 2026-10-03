# Expenses and calendar

Expenses originate from manual entry or conversational capture, normally WhatsApp, or explicit recurring-commitment payment recording, and appear as records users can correct or soft-delete. Dashboard values use the active profile's timezone and currency. Editing or deleting an expense marks affected calendar aggregates for rebuilding and refreshes the live monthly commitment.

## Read contracts

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/expenses/calendar?month=YYYY-MM` | Required month. Returns `{ month, currency, timezone, recordedDays, transactionCount, totalSpend, highestSpend, intensityMethod, days, commitmentSpend, creditCardSpend }`; a day is `{ date, transactionCount, totalSpend, intensity }`. Invalid month: `400 INVALID_MONTH`. | `App.svelte` loads; `Home.svelte` renders month grid. |
| `GET /api/web/expenses` | Optional `month` (defaults to user current month), `date`, `category`, `subcategory`, `beforeId`, `limit` (default 20). Returns `{ items, nextBeforeId, filterSummary }`. An item is `{ id, originalMessage, amount, currency, transactionTime, category, subcategory, merchantId, merchant, accountId, sourceAccount, commitmentPayment }`. | App requests five recent items; Home requests up to 50 for a calendar day. |
| `GET /api/web/expenses/options` | Returns `{ categories, merchants, accounts }`; categories are `{ name, subcategories }`, references `{ id, name }`. | Home edit-expense dialog and shared manual-entry form. |
| `GET /api/web/expense-taxonomy` | Full configured taxonomy. | Implemented, no current UI caller. |

`filterSummary` contains `transactionCount`, `totalAmount`, `currency`, `category`, `subcategory`, `tagIds`, and `tagMatch`. The UI currently exposes neither category filters nor cursor pagination.

## Mutation contracts

| Endpoint | Request and behavior | Frontend owner |
| --- | --- | --- |
| `PATCH /api/web/expenses/{id}` | Send at least one of `{ amount, transactionDate, category, subcategory, merchantId, accountId }`. Amount must be positive; category/subcategory must be a valid pair; reference IDs must be active and user-owned. Returns updated item. | Home edit dialog, then refreshes calendar, recent items, monthly commitment. |
| `DELETE /api/web/expenses/{id}` | Soft-deletes an owned visible expense. Returns `204`; inaccessible/missing is `404 EXPENSE_NOT_FOUND`; a linked occurrence/extra payment returns `409 COMMITMENT_PAYMENT_LINKED` (undo is not yet supported). | Home delete confirmation, then refreshes the three sections. |
| `POST /api/web/expenses/calendar/context` | Body `{ "type": "MISSING_TRANSACTION_DATE", "date": "YYYY-MM-DD", "timezone": "IANA zone" }`. Date cannot be future. Returns `{ contextId, status: "ACTIVE", date, expiresAt, whatsappUrl? }`. | Home calls it for an empty past day and shows WhatsApp handoff. |

The context expires after `app.web.action-context-expiry` (30 minutes by default), replaces another active context, and associates the next WhatsApp message with its date unless that message explicitly names a date. `whatsappUrl` is omitted if no WhatsApp destination is configured.
# Unified financial activity

`GET /api/web/activity?month=YYYY-MM` returns `{ month, currency, items }` for the authenticated active profile. `month` defaults to the profile's current month. Items are ordered newest first, with `type`, source `id`, actual `date`, `amount`, `label`, and `description`. The feed includes expense transactions, linked recurring-payment transactions labelled `COMMITMENT`, paid loan EMIs, confirmed investments, saved contributions and separately recorded CARD_PAYMENT settlements. A linked recurring payment is omitted from the ordinary `EXPENSE` branch and appears once with its transaction ID, actual amount and actual payment date; extras appear as separate payments on their actual dates. Planned/due/skipped rows and opening investments are excluded. Loans, investing and savings remain separately owned and do not join recorded spending in this release.

Home Activity exposes wrench/Edit and Delete buttons only for recorded `EXPENSE` items, disabled while offline. Edit resolves the full row through the date-filtered expense list and its cursor before opening the existing dialog; optional unset references are omitted from PATCH. Delete opens confirmation. Both mutations refresh the recorded feed, calendar, recent expenses and monthly commitment; failures are shown for retry. Recorded `COMMITMENT`, loan, investment and savings items have no expense mutation buttons.

Home combines this recorded feed with pending occurrences from the existing commitment, loan, and mutual-fund list APIs in one activity section. Server-reported `DUE` items receive priority before descending date order. Review opens the owning source's existing payment details. Payment completion refreshes both the source list and recorded feed; completed occurrences do not appear twice as planned and recorded items.

`commitmentSpend` is the included subtotal of visible transactions with an explicit recurring-payment reference, not an amount to add to `totalSpend`. `commitmentPayment` identifies these records in expense lists. Draftless payment items use their saved description for `originalMessage`. Captured expenses retain their original draft and `CAPTURE` origin; explicitly entered payments have `COMMITMENT_PAYMENT` origin and a unique payment reference. Linked amount/date corrections update payment history atomically.

## Free manual expense preparation

`POST /api/web/expenses/manual` requires `WEB_SESSION`, resolves the active real/demo profile, and returns `Cache-Control: no-store`. Body: `{requestId: UUID, date: "YYYY-MM-DD", amount: decimal, category: string, subcategory: string, merchant?: string, account?: string}`. No owner ID is accepted. Date must be past/current in the profile timezone. Amount rounds HALF_UP to two decimals, must remain positive, and allows at most 17 integer digits. Category/subcategory must form a configured taxonomy pair. Merchant/account are optional, trimmed, at most 200 characters. Full owned active canonical names take precedence; an unambiguous owned alias resolves to its canonical name. Explicit new names are permitted and created only at confirmation. Ambiguous aliases return `422 CAPTURE_AMBIGUOUS_REFERENCE`.

Returns `{status:"READY",answer,extractionId,preview:{amount,date,category,subcategory,merchant,account,currency}}`. A `WEB_APP` draft and ACTIVE extraction retain the server preview; preparation creates neither a transaction nor a saved name. Confirmation and cancellation use the existing `/api/web/expense-chat/capture/{extractionId}/confirm` and `/cancel` commands. Both paths share ownership, taxonomy, reference, recurring-match, aggregate-refresh and idempotent confirmation behavior.

The manual request namespace is separate from AI requests. Concurrent identical normalized retries with the same profile/request ID return the same extraction, including after recording; changed details return `409 MANUAL_REQUEST_CONFLICT`. Retrying a cancelled request returns `409 CAPTURE_CLOSED`; editing creates a new request ID after cancelling the previous preview. Invalid input returns `400 INVALID_MANUAL_EXPENSE`; authentication errors follow the existing web contract.

Add expense defaults to Enter manually in the frontend. The form provides amount, selected date, category/subcategory, and searchable saved merchant/account choices with explicit new-name entry. Describe with AI is optional and metered. Manual preparation and confirmation neither load credits as a prerequisite nor access the model, wallet, tariff, or AI daily budget. Exhausted/paused credits, missing AI configuration and a failed credit endpoint do not block manual entry. Both methods review a server-owned preview before Record expense; View expense opens the recorded date.

`creditCardSpend` is the included subtotal of visible purchase transactions against configured card accounts (including inactive configurations for historical labelling). It is not added to totalSpend. Card bill payments live in separate storage and are excluded from spending counts, category totals and calendar intensity. CARD_PAYMENT activity uses the actual payment date and explains that purchases were already counted in spending.

Configured-card purchase Activity rows say “On credit card · bill paid separately”; ordinary purchases retain “Recorded expense”.

Unified Accounts reuses account IDs/names returned by expense options; configured names are immediately selectable. Setup provides identity/type and card-cycle context, without recording received money or computing bank balances. Card settlements remain separate from expense totals and counts.

AI capture account-alias acceptance: explicit payment wording such as “paid from upi” resolves an unambiguous active account alias owned by the current profile, even when the model omits the account. An unsaved payment method does not imply an account; aliases shared by multiple accounts remain blank. Matching uses the complete alias, not a prefix. Applies to WhatsApp and web; verification: `WebExpenseCaptureServiceTest`.
