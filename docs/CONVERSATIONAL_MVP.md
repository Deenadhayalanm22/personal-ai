# Conversational MVP

## Product contract

The assistant records a useful observation before asking for enrichment. Missing
account balances do not invalidate expenses or account creation. They only limit
which claims the assistant can safely make.

| State | Available insight |
|---|---|
| Recorded | Activity totals, categories, dates and merchants |
| Linked | Activity by account or value container |
| Reconciled | Exact current balance or stock |

An unknown balance must remain `null`; it must never be changed to zero.

## First expense

1. Save amount, inferred category and date immediately.
2. Ask one high-value question: Cash, Bank / UPI, or Skip.
3. If the selected account type does not exist, create a provisional container.
4. Link the expense to that container.
5. Apply a balance mutation only when the container has a known current value.
6. Otherwise retain `needs_enrichment=true` and explain that spending insights
   work but exact balance is not updated.

## Account setup

Only account type and friendly name block creation. Currency defaults to INR for
the current MVP profile. Balance, safe reference, credit limit and due day are
optional. A later balance should be represented as a dated reconciliation in the
next phase rather than silently overwriting history.

## Conversation rules

- Sessions are stored per internal user and channel.
- WhatsApp sender IDs resolve to separate internal users.
- Incoming WhatsApp message IDs are persisted for idempotency.
- `skip`, `later`, `not sure`, and `don't know` end enrichment without losing the
  already recorded activity.
- `cancel` and `stop` end the question flow without deleting recorded activity.
- A new transaction-like sentence interrupts a pending follow-up and is handled
  as a new event.
- Button IDs are commands; visible titles are presentation only.

## Production follow-ons

- Dated balance and inventory reconciliation events.
- Account/product aliases and confirmed learned defaults.
- A durable correction/reversal flow with Undo and Change actions.
- Expiring, session-bound interactive action tokens.
- Distributed per-user ordering when more than one application instance runs.
- Inventory observations and pending quantity mutations using the same recorded,
  linked and reconciled states.
