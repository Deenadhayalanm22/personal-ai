# Mutual funds

Mutual-fund tracking selects a verified MFAPI scheme, maintains a holding and SIP occurrences, and calculates current valuation. `Home.svelte` owns the money-view flow.

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/mutual-funds/search?q=text` | Required query. Array of `{ schemeCode, schemeName, latestNav }`. | Scheme picker; starts search at two characters. |
| `POST /api/web/mutual-funds` | `{ schemeCode, schemeName, isin?, monthlySipAmount?, sipDay?, startMonth?, existingHolding? }`; holding is `{ currentUnits, totalInvestedAmount }`. Returns summary with `201`. | SIP creation; optional opening holding. |
| `GET /api/web/mutual-funds` | `{ mutualFunds }`; a summary includes identifiers/name, invested/current value/P&L/latest NAV, and `activeSip?`. | Portfolio total and fund cards. |
| `GET /api/web/mutual-funds/{id}` | `{ id, schemeName, invested, currentValue, profitOrLoss, profitOrLossPercent, averageNav, currentNav, units }`. | Fund-detail dialog. |
| `POST /api/web/mutual-funds/{id}/lump-sums` | `{ amount, transactionDate, nav?, units?, calculationSource }`; NAV or units is sufficient, returns confirmed transaction with `201`. | API wrapper exists; no current UI caller. |
| `POST /api/web/mutual-funds/{id}/sip-occurrences/{month}/confirm` | Same body; month is `YYYY-MM`; returns confirmed occurrence. | SIP-due prompt sends `NAV_ESTIMATED`, amount, date, NAV. |

A SIP requires amount, day 1–28, and start month. Creation adds an initial scheduled/due occurrence; scheduler adds a due occurrence for current month. Confirmations require positive amount, date, a calculation source (`USER_ENTERED`, `NAV_ESTIMATED`, `STATEMENT_VERIFIED`), and NAV or units. Only confirmed transactions contribute to invested value/units. Current valuation/P&L are `null` without latest NAV. Errors: `409 INVESTMENT_EXISTS`, `404 INVESTMENT_NOT_FOUND`, `400 INVALID_MUTUAL_FUND`.
