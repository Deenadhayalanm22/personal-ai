# Money Stories Frontend API Contracts

The home screen uses three independent business APIs. Calendar, recent transactions, and Money Stories must not be combined.

## 1. Calendar

`GET /api/web/expenses/calendar?month=YYYY-MM`

```json
{
  "month": "2026-08",
  "currency": "INR",
  "timezone": "Asia/Kolkata",
  "recordedDays": 12,
  "transactionCount": 29,
  "totalSpend": 18450.00,
  "highestSpend": 4250.00,
  "intensityMethod": "month-max-v1",
  "days": [
    { "date": "2026-08-01", "transactionCount": 2, "totalSpend": 840.00, "intensity": 1 }
  ]
}
```

- Return every date in the requested month, ordered ascending.
- Group confirmed, non-deleted expenses using the user's IANA timezone.
- Backend calculates `intensity` from 0–4; frontend maps it to green colors.
- Level 0 means no spending. Levels 1–4 represent up to 25%, 50%, 75%, and above 75% of `highestSpend`.
- Do not include recent transactions or stories in this response.

## 2. Recent transactions

`GET /api/web/expenses?month=YYYY-MM&limit=10`

```json
{
  "items": [
    {
      "id": 481,
      "merchant": "Swiggy",
      "category": "Food & Dining",
      "transactionDate": "2026-08-29T20:15:00+05:30",
      "amount": 640.00
    }
  ],
  "nextBeforeId": 470
}
```

- Order newest first, using ID descending as a stable tie-breaker.
- The Recent tab requests only the first five items and does not paginate.
- For selected-day activity, support the optional `date=YYYY-MM-DD` filter: `GET /api/web/expenses?month=2026-08&date=2026-08-15&limit=50`.
- When `date` is provided, return only transactions assigned to that local date in the user's timezone.
- Do not include provisional, draft, deleted, or enrichment-state transactions.

## 3. Money Stories

`GET /api/v2/web/expenses/monthly?month=YYYY-MM`

```json
{
  "month": "2026-08",
  "currency": "INR",
  "stories": [
    {
      "id": "story_2026_08_food_weekends",
      "type": "SPENDING PATTERN",
      "headline": "Weekend food spending increased",
      "summary": "You spent more on food during weekends than weekdays this month.",
      "explanation": "Six weekend food transactions contributed most of the change.",
      "whyItMatters": "This explains where additional food spending occurred without treating it as a warning.",
      "amount": 3280.00,
      "impactPercent": 68,
      "evidence": [
        {
          "transactionId": 481,
          "merchant": "Swiggy",
          "category": "Food & Dining",
          "transactionDate": "2026-08-29T20:15:00+05:30",
          "amount": 640.00
        }
      ]
    }
  ]
}
```

### Discretionary-versus-essential weekly story

For a discretionary story, include the following optional fields. The web client uses them to render a three-card narrative only when the values are present, so it never estimates a user's essential spending.

```json
{
  "type": "DISCRETIONARY SPENDING",
  "headline": "Your small discretionary spends added up this week",
  "summary": "12 discretionary purchases came to ₹1,240.",
  "periodLabel": "4–10 August",
  "discretionarySpend": 1240.00,
  "essentialSpend": 3100.00,
  "comparisonPercent": 40,
  "discretionaryTransactionCount": 12
}
```

- Emit this story only when the selected seven-day period contains more than 10 confirmed discretionary transactions and there is comparable essential spending in the same period.
- `comparisonPercent` is `round(discretionarySpend / essentialSpend * 100)`. It answers: “for every ₹100 spent on essentials, how much went to discretionary choices?”
- Use the user's category taxonomy to determine essential and discretionary groups. Do not include savings, transfers, repayments, refunds, or uncategorised transactions in either group.
- The story should be observational and non-judgmental; it helps a user notice a pattern and choose whether to adjust it.

- Return zero to five evidence-backed stories, ordered by relevance.
- An empty `stories` array is a valid response.
- Story IDs must remain stable for the same user, month, type, and calculation version.
- Evidence transactions must belong to the authenticated user.
- Do not return chart images, renderer types, visualization metadata, analysis intent, or presentation mood.

## Shared behavior

- All endpoints use the existing secure authenticated session.
- Invalid `month` returns `400 INVALID_MONTH`.
- Missing or expired session returns `401 UNAUTHORIZED`.
- A valid month without data returns `200` with zero or empty values.
- Register static `/expenses/calendar` before `/expenses/{id}`, or constrain `{id}` to numeric values.

## 4. Prepare missing-date WhatsApp context

`POST /api/web/expenses/calendar/context`

```json
{
  "type": "MISSING_TRANSACTION_DATE",
  "date": "2026-08-14",
  "timezone": "Asia/Kolkata"
}
```

Successful response — `201 Created`:

```json
{
  "contextId": "ctx_01K4A7J52J6J6M",
  "status": "ACTIVE",
  "date": "2026-08-14",
  "expiresAt": "2026-08-31T14:45:00Z",
  "whatsappUrl": "https://wa.me/919999999999"
}
```

### Backend behavior

- Resolve the user exclusively from the authenticated session; never accept a user ID from the request.
- Validate that `date` is a real ISO local date and is not in the future in the supplied/user timezone.
- Store an active context associated with the authenticated user, selected date, context type, timezone, creation time, and expiry time.
- Use a short TTL, recommended 30 minutes.
- Creating another context of the same type replaces or supersedes the user's previous active context.
- When the next eligible inbound WhatsApp expense message arrives, atomically claim the active context and use its date as the transaction date.
- Mark the context `CONSUMED` only after the expense is successfully persisted. If processing fails, leave it available for safe retry until expiry.
- A date explicitly included in the WhatsApp message takes precedence over stored context; mark the stored context superseded or consumed according to backend audit policy.
- Context must be single-use and protected against two concurrent inbound messages consuming it.
- Expired context must never affect a later message.
- `whatsappUrl` is optional. If returned, it must be the application's configured WhatsApp destination, never a client-provided number.

### Stored context fields

At minimum: `id`, `userId`, `type`, `contextDate`, `timezone`, `status`, `createdAt`, `expiresAt`, `consumedAt`, and optionally `consumedTransactionId` and `supersededById`.

### Errors

- `400 INVALID_CONTEXT_DATE`: malformed or future date.
- `401 UNAUTHORIZED`: missing or expired web session.
- `409 CONTEXT_CONFLICT`: context could not be safely replaced.
- `500 CONTEXT_CREATE_FAILED`: unexpected persistence failure.

## 5. Transaction actions

Controlled edit options:

`GET /api/web/expenses/options`

Returns category names with their allowed subcategories, plus merchants and accounts as `{ "id", "name" }` objects. The Quick edit form uses only these returned values and filters the subcategory list by the selected category.

Quick transaction edit:

`PATCH /api/web/expenses/{id}`

```json
{
  "amount": 640.00,
  "transactionDate": "2026-08-15",
  "category": "Food & Dining",
  "subcategory": "Restaurant & Cafe",
  "merchantId": 7,
  "accountId": 3
}
```

Amount and transaction date remain editable inputs. Category, subcategory, merchant, and source account are controlled selections populated by the edit-options API.

Successful update returns the complete updated transaction. Reject non-positive amounts, invalid/future dates, and transactions that do not belong to the authenticated user.

Delete:

`DELETE /api/web/expenses/{id}`

Deletion uses the authenticated user's transaction ID only. A successful deletion returns `204 No Content`. Return `404` when the transaction does not exist or does not belong to the authenticated user. After `204`, the frontend performs a full page refresh so every calendar, activity, and story projection is reloaded.

## 6. Reference preferences

When the user first opens Normalization, request `GET /api/web/reference-entity-types`. Cache the successful response in memory and use `entityTypes` to populate the form's first dropdown.

Create a preference with `POST /api/web/reference-preferences`:

```json
{
  "entityType": "MERCHANT",
  "primaryReference": "Amazon",
  "alias": "AMZN, Amazon India, Amazon Store"
}
```

All three fields are required. `entityType` must be one of the values returned by the entity-types endpoint. The alias is submitted as one comma-separated string; the backend trims values, ignores empty values, and removes case-insensitive duplicates. Both endpoints use the authenticated `WEB_SESSION` cookie and never accept a user ID.

List the authenticated user's saved preferences with `GET /api/web/reference-preferences`. The response wraps the collection in `references`:

```json
{
  "references": [
    {
      "referenceId": 7,
      "entityType": "MERCHANT",
      "primaryReference": "Amazon",
      "aliases": [{ "aliasId": 9, "alias": "AMZN" }]
    }
  ]
}
```

The Normalization screen loads this collection when it opens and refreshes it after creation so the canonical server state is displayed.
