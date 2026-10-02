# Stocks

Stock holdings support add, view, manual purchases, investment corrections, monthly purchase decisions, and delete. The backend stores a selected listed symbol plus the user's opening quantity and invested amount. `StockMarketDataAdapter` is the provider boundary; the initial implementation uses Yahoo Finance search and chart endpoints behind that adapter.

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/stocks/search?q=text` | Required query with at least two characters. Array of `{ symbol, name, exchange, latestPrice }`; price is `null` when chart lookup is unavailable. | Stock picker. |
| `POST /api/web/stocks` | `{ symbol, name, exchange?, quantity, totalInvestedAmount }`. Returns the stock summary with `201`. | Add-stock form. |
| `GET /api/web/stocks` | `{ stocks }`; each stock includes symbol/name/exchange, quantity, invested/current value/P&L, and `latestPrice`. | Compact stock portfolio cards. |
| `GET /api/web/stocks/{id}` | Stock summary plus confirmed opening, monthly-plan and manual purchase history, and skipped monthly occurrences. | Stock-detail window. |
| `DELETE /api/web/stocks/{id}` | Deletes the current user's stock holding and its opening transaction; returns `204`. A holding owned by someone else or not found returns `404 STOCK_NOT_FOUND`. | Stock details. |
| `POST /api/web/stocks/{id}/purchases` | `{ amount, transactionDate, units }` adds a confirmed manual purchase; returns refreshed detail. | Stock details. |
| `PATCH /api/web/stocks/{id}/transactions/{transactionId}` | `{ amount, transactionDate, units }` corrects an owned confirmed investment and recalculates its execution price. | Edit stock action in View details. |
| `POST /api/web/stocks/{id}/monthly-plan-occurrences/{month}/skip` | Skips a due monthly purchase once; returns the occurrence. | Due decision in stock details. |
| `POST /api/web/stocks/{id}/monthly-plan` | `{ amount, day, startMonth }`; saves an active ETF monthly plan and returns the stock summary. | ETF-plan form. |
| `POST /api/web/stocks/{id}/monthly-plan-occurrences/{month}/confirm` | `{ amount, transactionDate?, executionPrice, units? }`; confirms an ETF purchase, calculating units when omitted. | Due ETF-plan confirmation. |

An optional monthly plan requires a positive amount, day 1–28, and a start month. Its due occurrence is confirmed with the actual execution price; it automatically contributes to Monthly Commitment as Planned investing. The chart-derived latest price is read at list time. If the provider cannot supply a price, `latestPrice`, `currentValue`, `profitOrLoss`, and `profitOrLossPercent` are `null`; the recorded quantity and invested amount remain available. Errors: `400 INVALID_STOCK`, `400 INVALID_STOCK_SEARCH`, `404 STOCK_NOT_FOUND`, `409 STOCK_EXISTS`, and `502 STOCK_LOOKUP_FAILED`.

The compact card shows valuation and a due strip. Plan setup, due confirmation or skip, direct history, manual purchase, investment correction, and delete are in View details. Scheduled purchases cannot be confirmed or skipped before their configured same-month advance window; completed and skipped purchases cannot be acted on again.

The stock detail layout places the title above right-aligned Edit stock and Delete stock actions. Add stock purchase is beside total P&L above the holding grid. Individual history rows are read-only in the portal. A due stock commitment uses the same red row treatment as due funds and loans, and the card combines Due now with View details in one red-bordered strip.

Home also reads the stock list at startup to render active monthly-plan calendar markers and pending Activity. It uses activeMonthlyPlan amount/day/startMonth and currentMonthlyPlan month/status; Review opens stock details. Confirmed/skipped purchases are omitted from pending Activity, and skipped plans are omitted from calendar markers for that month.

## Configurable advance payment window

Pay and Skip for recurring commitments (including daily/weekly dates), loan EMIs, mutual-fund SIPs, and stock monthly plans open five days before the scheduled due date by default, clamped to the first day of that same calendar month. `PAYMENT_ADVANCE_DAYS` configures `app.payments.advance-days`; zero restores due-day eligibility. The server reports eligible unresolved occurrences as `DUE`, so Monthly Commitment evidence, Money details and Home Activity share the same eligibility. Saved completed/skipped outcomes remain final; scheduled due dates and planning amounts are unchanged. Actual payment dates remain the dates the user records.

Acceptance coverage: due October 15 is disabled October 9 and enabled October 10; due October 5 is enabled October 1; due October 1 is disabled September 28. The same rule blocks December-to-January early actions and supports configured two-day and zero-day windows. Covered by `PaymentActionWindowTest` and the source service tests.
