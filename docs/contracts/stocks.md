# Stocks

Stock holdings support add, view, and delete in this release. The backend stores a selected listed symbol plus the user's opening quantity and invested amount. `StockMarketDataAdapter` is the provider boundary; the initial implementation uses Yahoo Finance search and chart endpoints behind that adapter.

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/stocks/search?q=text` | Required query with at least two characters. Array of `{ symbol, name, exchange, latestPrice }`; price is `null` when chart lookup is unavailable. | Stock picker. |
| `POST /api/web/stocks` | `{ symbol, name, exchange?, quantity, totalInvestedAmount }`. Returns the stock summary with `201`. | Add-stock form. |
| `GET /api/web/stocks` | `{ stocks }`; each stock includes symbol/name/exchange, quantity, invested/current value/P&L, and `latestPrice`. | Stock portfolio cards. |
| `GET /api/web/stocks/{id}` | Stock summary plus confirmed opening and monthly-plan purchase history. | Stock-detail window. |
| `DELETE /api/web/stocks/{id}` | Deletes the current user's stock holding and its opening transaction; returns `204`. A holding owned by someone else or not found returns `404 STOCK_NOT_FOUND`. | Stock-card delete control. |
| `POST /api/web/stocks/{id}/monthly-plan` | `{ amount, day, startMonth }`; saves an active ETF monthly plan and returns the stock summary. | ETF-plan form. |
| `POST /api/web/stocks/{id}/monthly-plan-occurrences/{month}/confirm` | `{ amount, transactionDate?, executionPrice, units? }`; confirms an ETF purchase, calculating units when omitted. | Due ETF-plan confirmation. |

An optional monthly plan requires a positive amount, day 1–28, and a start month. Its due occurrence is confirmed with the actual execution price; it automatically contributes to Monthly Commitment as Planned investing. The chart-derived latest price is read at list time. If the provider cannot supply a price, `latestPrice`, `currentValue`, `profitOrLoss`, and `profitOrLossPercent` are `null`; the recorded quantity and invested amount remain available. Errors: `400 INVALID_STOCK`, `400 INVALID_STOCK_SEARCH`, `404 STOCK_NOT_FOUND`, `409 STOCK_EXISTS`, and `502 STOCK_LOOKUP_FAILED`.
