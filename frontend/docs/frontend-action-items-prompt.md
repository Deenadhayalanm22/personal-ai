# Frontend prompt: common action items

Add a reusable **Needs your attention** section to the Expense AI web app. This is a common action queue, not a loan-only feature. It will later contain mutual-fund confirmations, reminders, and other user decisions.

## API

All requests use the existing authenticated `WEB_SESSION` cookie.

### Get open actions

`GET /api/web/actions`

Only unresolved actions are returned. Once an action is completed, it must no longer appear in this section.

```json
{
  "actions": [
    {
      "id": 42,
      "actionType": "LOAN_CLOSURE_CONFIRMATION",
      "referenceType": "LOAN",
      "referenceId": 17,
      "title": "Confirm loan closure",
      "description": "Credit card - EMI was scheduled to finish on 2026-07-05. Mark it closed if the final EMI was paid.",
      "scheduledCompletionDate": "2026-07-05"
    }
  ]
}
```

### Complete an action

`POST /api/web/actions/{id}/complete`

No request body is needed. For the current action type, this marks the linked loan as `CLOSED` and resolves the action. Remove the card from the UI after a successful response, then refresh loans/actions as needed.

## Current action type

Handle `LOAN_CLOSURE_CONFIRMATION` with a card such as:

```text
Loan closure confirmation
Credit card - EMI was scheduled to finish on 5 Jul 2026.
[ Mark loan closed ]
```

- The only available CTA for MVP is **Mark loan closed**.
- Do not add “keep active,” snooze, dismissal, edit-loan, or notification-preference controls yet.
- Show an inline loading state while completing the action and show the existing API error pattern if it fails.
- If there are no open actions, hide this section completely.

## Placement and styling

- Place this above the passive “Recent activity” list, or as a clearly visible card at the top of the “Your money” panel.
- Use the app’s existing card, icon, button, loading, and money-formatting styles.
- Do not hard-code loan-specific logic outside the `actionType` renderer. Future action types will use the same API and section.

## Product behavior

The backend evaluates action candidates as part of the existing daily aggregation workflow and when an admin runs the existing WhatsApp `/aggregate` command. The frontend only needs to fetch and render open actions; it does not calculate whether an EMI plan has ended.
