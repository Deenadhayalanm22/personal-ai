# Mutual funds API contract

All endpoints require the authenticated browser's `WEB_SESSION` cookie. Amounts are INR numbers. Dates use `YYYY-MM-DD`; months use `YYYY-MM`.

The app does not persist a shared fund catalogue. Search MFAPI through the search endpoint and send the selected result's exact `schemeCode` and `schemeName` when creating a user's fund.

## 1. Search schemes

`GET /api/web/mutual-funds/search?q={query}`

`q` must contain at least two characters.

```json
[
  {
    "schemeCode": "122639",
    "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth"
  }
]
```

Use the returned full scheme name as the one-choice picker label. It includes the plan and option, so the frontend must not present separate Direct/Regular or Growth/IDCW fields.

## 2. Add a mutual fund

`POST /api/web/mutual-funds`

```json
{
  "schemeCode": "122639",
  "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
  "isin": "INF879O01027",
  "monthlySipAmount": 25000,
  "sipDay": 5,
  "startMonth": "2026-10",
  "existingHolding": {
    "currentUnits": 128.456,
    "totalInvestedAmount": 10000
  }
}
```

Required: `schemeCode`, `schemeName`.

To configure a SIP, provide all three fields: `monthlySipAmount` (> 0), `sipDay` (1–28), and `startMonth`. Omit all three for a holding with no SIP.

`existingHolding` is optional. If supplied, both `currentUnits` and `totalInvestedAmount` must be greater than zero. The backend records it as `OPENING_BALANCE` with `USER_ENTERED` accuracy and derives its average purchase cost.

Successful response: `201 Created`, returning a `MutualFund` object below. Creating the same selected scheme twice for one user returns `409 INVESTMENT_EXISTS`.

## 3. List holdings and activity

`GET /api/web/mutual-funds`

```json
{
  "mutualFunds": [
    {
      "id": 1,
      "schemeCode": "122639",
      "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
      "isin": "INF879O01027",
      "monthlySipAmount": 25000.00,
      "sipDay": 5,
      "startMonth": "2026-10",
      "sipStatus": "ACTIVE",
      "currentUnits": 128.456000,
      "totalInvestedAmount": 10000.00,
      "averagePurchaseCost": 77.847668,
      "activity": []
    }
  ]
}
```

`currentUnits`, `totalInvestedAmount`, and `averagePurchaseCost` are backend-calculated from `CONFIRMED` activity only. Do not calculate or overwrite them in the client.

## 4. Record a lump-sum investment

`POST /api/web/mutual-funds/{id}/lump-sums`

```json
{
  "amount": 25000,
  "transactionDate": "2026-09-07",
  "nav": 87.25,
  "calculationSource": "NAV_ESTIMATED"
}
```

`amount`, `transactionDate`, and `calculationSource` are required. Provide at least one of `nav` or `units`:

- `nav` only: backend derives `units`.
- `units` only: backend derives effective `unitPrice`.
- both: backend retains both values.

`calculationSource` values:

- `NAV_ESTIMATED` — NAV-led estimate.
- `STATEMENT_VERIFIED` — actual units/NAV verified from the statement.
- `USER_ENTERED` — user-provided data without statement verification.

Successful response: `201 Created` with a confirmed `Activity` object.

## 5. Confirm a due SIP

`POST /api/web/mutual-funds/{id}/sip-occurrences/{month}/confirm`

Example: `POST /api/web/mutual-funds/1/sip-occurrences/2026-10/confirm`

Use the same request body as a lump sum. This changes the existing SIP occurrence to `CONFIRMED`; it does not create a second transaction.

## Activity object

```json
{
  "id": 11,
  "transactionKind": "SIP",
  "status": "DUE",
  "scheduledMonth": "2026-10",
  "transactionDate": null,
  "amount": 25000.00,
  "unitPrice": null,
  "units": null,
  "calculationSource": null
}
```

`transactionKind` is `OPENING_BALANCE`, `SIP`, or `LUMPSUM` for the MVP. SIP statuses are `SCHEDULED`, `DUE`, `PENDING`, `CONFIRMED`, `SKIPPED`, and `FAILED`; currently the frontend can confirm a due SIP. Skip and remind-later endpoints are not implemented yet.

## Errors

Validation responses use `400` and the shape:

```json
{"code":"INVALID_MUTUAL_FUND","message":"sipDay must be between 1 and 28"}
```

Other relevant errors: `404 INVESTMENT_NOT_FOUND`, `404 SIP_OCCURRENCE_NOT_FOUND`, `409 INVESTMENT_EXISTS`, and `502 SCHEME_LOOKUP_FAILED`.
