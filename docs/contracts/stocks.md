# Stocks

Stock holdings are add-and-view only in this release. The backend stores a selected listed symbol plus the user's opening quantity and invested amount. `StockMarketDataAdapter` is the provider boundary; the initial implementation uses Yahoo Finance search and chart endpoints behind that adapter.

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/stocks/search?q=text` | Required query with at least two characters. Array of `{ symbol, name, exchange, latestPrice }`; price is `null` when chart lookup is unavailable. | Stock picker. |
| `POST /api/web/stocks` | `{ symbol, name, exchange?, quantity, totalInvestedAmount }`. Returns the stock summary with `201`. | Add-stock form. |
| `GET /api/web/stocks` | `{ stocks }`; each stock includes symbol/name/exchange, quantity, invested/current value/P&L, and `latestPrice`. | Stock portfolio cards. |

The chart-derived latest price is read at list time. If the provider cannot supply a price, `latestPrice`, `currentValue`, `profitOrLoss`, and `profitOrLossPercent` are `null`; the recorded quantity and invested amount remain available. Errors: `400 INVALID_STOCK`, `400 INVALID_STOCK_SEARCH`, `409 STOCK_EXISTS`, and `502 STOCK_LOOKUP_FAILED`.
