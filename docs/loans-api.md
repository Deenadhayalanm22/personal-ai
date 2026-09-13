# Loans API

All endpoints require the browser's `WEB_SESSION` cookie and operate only on the authenticated user's loans.

## Enums

`loanType` must be one of: `HOME`, `VEHICLE`, `PERSONAL`, `EDUCATION`, `CREDIT_CARD_EMI`, `OTHER`.

`status` is either `ACTIVE` or `CLOSED`. It is set only when a loan is created and cannot be changed through the edit endpoint.

## Create a loan

`POST /api/web/loans`

Required fields are `loanName`, `loanType`, `lenderName`, `originalPrincipal`, `monthlyEmiAmount`, `totalTenureMonths`, and `firstEmiDueDate`. `status` is optional and defaults to `ACTIVE`; `notes` is optional.

```json
{
  "loanName": "HDFC Home Loan",
  "loanType": "HOME",
  "lenderName": "HDFC Bank",
  "originalPrincipal": 5000000,
  "monthlyEmiAmount": 45000,
  "totalTenureMonths": 240,
  "firstEmiDueDate": "2025-04-05",
  "status": "ACTIVE",
  "notes": "Primary residence"
}
```

Returns `201 Created` and the created loan:

```json
{
  "id": 17,
  "loanName": "HDFC Home Loan",
  "loanType": "HOME",
  "lenderName": "HDFC Bank",
  "originalPrincipal": 5000000.00,
  "monthlyEmiAmount": 45000.00,
  "totalTenureMonths": 240,
  "firstEmiDueDate": "2025-04-05",
  "status": "ACTIVE",
  "notes": "Primary residence"
}
```

## List loans

`GET /api/web/loans`

Returns `200 OK`:

```json
{
  "loans": [
    {
      "id": 17,
      "loanName": "HDFC Home Loan",
      "loanType": "HOME",
      "lenderName": "HDFC Bank",
      "originalPrincipal": 5000000.00,
      "monthlyEmiAmount": 45000.00,
      "totalTenureMonths": 240,
      "firstEmiDueDate": "2025-04-05",
      "status": "ACTIVE",
      "notes": "Primary residence"
    }
  ]
}
```

## Edit a loan

`PATCH /api/web/loans/{id}`

Send at least one editable field. All fields in this request are optional; omitted fields stay unchanged. `status` must not be sent to this endpoint.

```json
{
  "monthlyEmiAmount": 47500.25,
  "totalTenureMonths": 228,
  "notes": "EMI revised"
}
```

Editable fields: `loanName`, `loanType`, `lenderName`, `originalPrincipal`, `monthlyEmiAmount`, `totalTenureMonths`, `firstEmiDueDate`, and `notes`.

An empty `notes` string clears existing notes. A JSON `null` is treated as no change.

Returns `200 OK` with the complete loan object. Returns `404` if the loan does not belong to the authenticated user.

## Validation and errors

- `loanName` and `lenderName` are trimmed, required, and limited to 255 characters.
- `originalPrincipal` and `monthlyEmiAmount` must be greater than zero and are stored to two decimal places.
- `totalTenureMonths` must be a positive integer.
- `firstEmiDueDate` uses ISO date format: `YYYY-MM-DD`.

Validation errors return `400` with the standard shape:

```json
{"code":"INVALID_LOAN","message":"monthlyEmiAmount must be greater than zero"}
```

An unavailable loan returns:

```json
{"code":"LOAN_NOT_FOUND","message":"Loan not found"}
```
