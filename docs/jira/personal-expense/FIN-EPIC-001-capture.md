# FIN-EPIC-001 — Conversational expense capture

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |
| Goal | Turn an eligible WhatsApp message into one safe, traceable expense record |
| Primary interfaces | WhatsApp webhook; expense dashboard |

## Functional boundary

The webhook accepts text, audio, and interactive WhatsApp payloads. Interactive confirmation replies are processed first. Text/audio messages may be handled as an administrative aggregate-backfill command; all other messages are persisted as idempotent drafts and passed to normalization. A successful confirmation produces a financial transaction and an outbound acknowledgement. The portal does not create raw expenses; it only displays and corrects captured ones.

## Cross-stack ownership

| Layer | Implemented responsibility |
| --- | --- |
| WhatsApp/controller/orchestration | Maps inbound payloads, writes idempotent drafts, normalizes new text, and handles confirm/discard buttons. |
| Expense services and database | Persist drafts, extractions, transactions, references, and confirmation outcome. |
| `frontend/src/App.svelte` | Refreshes calendar, recent activity, and stories from the captured transaction data. |
| `frontend/src/Home.svelte` | Lets the user inspect, edit, or delete a captured expense; it does not submit a new raw expense. |

## FIN-001 — Receive, route, and retain an inbound turn

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** text, audio, or interactive messages, **when** the webhook accepts them, **then** it returns `200` after synchronous processing and creates no browser session state.
2. **Given** a duplicate source-message ID, **when** delivery is retried, **then** draft routing prevents a duplicate capture effect.
3. **Given** a recognized interactive confirmation, **when** received, **then** it is handled before ordinary normalization and a successful record is acknowledged.
4. **Given** a supported aggregate-backfill command, **when** received, **then** it is handled without normal expense normalization.
5. **Given** an ordinary routed message, **when** captured, **then** original evidence and source identity remain available through the draft/transaction relationship.

### Integration-test scenarios

- POST a text payload and assert the draft/normalization path executes once.
- Replay the payload and assert it has no second transaction effect.
- POST an interactive confirmation and assert it precedes the ordinary-message route.

## FIN-002 — Normalize and confirm an expense

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** valid normalized facts and confirmation, **when** written, **then** the transaction has positive amount, date, valid category/subcategory, owner, and source draft.
2. **Given** incomplete or low-confidence extraction, **when** confirmation is required, **then** no transaction is written until that confirmation succeeds.
3. **Given** a merchant or source account, **when** normalization resolves it, **then** it is stored as a user-owned reference.
4. **Given** invalid parsed data or taxonomy pair, **when** a write is attempted, **then** it fails without a partial transaction.
5. **Given** a recorded transaction, **when** notification is sent, **then** it describes the persisted result rather than a prediction.

### Integration-test scenarios

- Exercise a valid message-to-confirmation flow and assert one owned transaction, its draft, classification, and references.
- Submit invalid amount/category/date data and assert transaction count is unchanged.
- Confirm the same stored draft twice and assert no duplicate transaction.

## Non-goals and guardrails

- Capture does not create account balances, transfers, income, or a general ledger movement.
- The current product does not offer conversational edit/undo; portal correction is in FIN-EPIC-002.
