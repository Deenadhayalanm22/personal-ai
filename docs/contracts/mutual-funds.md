# Mutual funds

Mutual-fund tracking selects a verified MFAPI scheme, maintains a holding and SIP occurrences, and calculates current valuation. `Home.svelte` owns the money-view flow.

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/mutual-funds/search?q=text` | Required query. Array of `{ schemeCode, schemeName, latestNav }`. | Scheme picker; starts search at two characters. |
| `POST /api/web/mutual-funds` | `{ schemeCode, schemeName, isin?, monthlySipAmount?, sipDay?, startMonth?, existingHolding? }`; holding is `{ currentUnits, totalInvestedAmount }`. Returns summary with `201`. | SIP creation; optional opening holding. |
| `GET /api/web/mutual-funds` | `{ mutualFunds }`; a summary includes identifiers/name, invested/current value/P&L/latest NAV, and `activeSip?`. | Portfolio total and fund cards. |
| `GET /api/web/mutual-funds/{id}` | `{ id, schemeName, invested, currentValue, profitOrLoss, profitOrLossPercent, averageNav, currentNav, units, history }`; `history` is chronological confirmed opening-holding, SIP, and lump-sum activity with date, type, amount, NAV, units, and `scheduledMonth` for SIP rows. | Fund-detail dialog. |
| `POST /api/web/mutual-funds/{id}/lump-sums` | `{ amount, transactionDate, nav?, units?, calculationSource }`; NAV or units is sufficient, returns confirmed transaction with `201`. | Fund-card `＋ Lump sum` action. |
| `POST /api/web/mutual-funds/{id}/sip-occurrences/{month}/confirm` | Same body; month is `YYYY-MM`; returns confirmed occurrence. | SIP-due prompt sends `NAV_ESTIMATED`, amount, date, NAV. |
| `PATCH /api/web/mutual-funds/{id}/sip-occurrences/{month}` | Same body; corrects an already confirmed occurrence and returns its corrected allocation. | Mutual Funds section. |
| `PATCH /api/web/mutual-funds/{id}/transactions/{transactionId}` | Same body; corrects a confirmed opening holding, SIP, or lump sum and returns the corrected transaction. | Fund-detail history row. |

A SIP requires amount, day 1–28, and start month. A plan created during its requested start month first occurs in the following month, so adding a fund mid-month never creates a retroactive confirmation request. Otherwise creation adds the start-month occurrence; it is `SCHEDULED` before its configured local SIP day and becomes `DUE` on that day. Reading a user's mutual funds ensures the current-month occurrence exists and promotes an eligible scheduled occurrence to due, so a scheduler delay cannot hide a due SIP. Mutual-fund list entries expose their current SIP occurrence when one exists, including its month, status, allocation facts, and source. Confirmations require positive amount, date, a calculation source (`USER_ENTERED`, `NAV_ESTIMATED`, `STATEMENT_VERIFIED`), and NAV or units. A confirmed occurrence can be corrected from the Mutual Funds section; holdings are recalculated from confirmed transactions. Only confirmed transactions contribute to invested value/units. Current valuation/P&L are `null` without latest NAV. Errors: `409 INVESTMENT_EXISTS`, `404 INVESTMENT_NOT_FOUND`, `400 INVALID_MUTUAL_FUND`.
