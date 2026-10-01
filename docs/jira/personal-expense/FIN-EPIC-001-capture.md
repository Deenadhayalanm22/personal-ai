# FIN-EPIC-001 — Conversational expense capture

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |
| Goal | Turn an eligible WhatsApp or authenticated web description into one safe, traceable expense record |
| Primary interfaces | WhatsApp webhook; expense dashboard; Ask AI |

## Functional boundary

The webhook accepts text, audio, and interactive WhatsApp payloads. Interactive confirmation replies are processed first. Text messages are persisted as idempotent drafts and passed to normalization. Audio messages are persisted as drafts, transcribed, and shown to the sender for word-level confirmation or discard before expense extraction. Confirmed words follow the ordinary expense extraction and confirmation flow; discarded audio never creates a transaction. Text/audio messages may be handled as an administrative aggregate-backfill command. The first newly routed text or audio message each day starts background aggregation for missed dates and the previous day, followed by daily action evaluation. A failed background run is retried on the next message. A successful expense confirmation produces a financial transaction and an outbound acknowledgement. The portal also prepares authenticated conversational expense drafts in Ask AI, then records only the explicitly confirmed server preview.

## Cross-stack ownership

| Layer | Implemented responsibility |
| --- | --- |
| WhatsApp/controller/orchestration | Maps inbound payloads, writes idempotent drafts, normalizes new text, and handles confirm/discard buttons. |
| Expense services and database | Persist drafts, extractions, transactions, references, and confirmation outcome. |
| `frontend/src/App.svelte` | Refreshes calendar, recent activity, and stories from the captured transaction data. |
| `frontend/src/Home.svelte` | Lets the user inspect, edit, or delete a captured expense; it opens Ask AI to prepare a date-scoped draft; it does not write an expense directly. |

## FIN-001 — Receive, route, and retain an inbound turn

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** text, audio, or interactive messages, **when** the webhook accepts them, **then** it returns `200` after synchronous processing and creates no browser session state.
2. **Given** a duplicate source-message ID, **when** delivery is retried, **then** draft routing prevents a duplicate capture effect.
3. **Given** a recognized interactive confirmation, **when** received, **then** it is handled before ordinary normalization and a successful record is acknowledged.
4. **Given** a supported aggregate-backfill command, **when** received, **then** it is handled without normal expense normalization.
5. **Given** an ordinary routed message, **when** captured, **then** original evidence and source identity remain available through the draft/transaction relationship.
6. **Given** the first newly routed WhatsApp text or audio message of a day in the aggregation timezone, **when** captured, **then** missed daily aggregates and yesterday's aggregates are rebuilt asynchronously and daily actions are evaluated once; a failed run can retry on a later message.
7. **Given** a newly routed audio message, **when** it is transcribed, **then** the staged draft retains the recognized words and sends them for review without extracting or recording an expense.
8. **Given** the owner confirms the recognized words, **when** the audio review reply arrives, **then** those words enter the existing expense extraction and confirmation flow. Discard cancels the draft; repeated or foreign replies cannot create a transaction.
9. **Given** transcription renders a spoken amount with a currency label or English number words, **when** the review text is staged, **then** the amount is shown as digits without an inferred currency label; the reviewed wording is also used for expense extraction.
10. **Given** audio spoken in another language or in mixed transliteration such as Tanglish, **when** transcribed, **then** its natural-language wording is retained in the draft and review message. After approval, expense extraction maps category and subcategory to the configured English taxonomy labels.
11. **Given** an extraction without a positive amount or complete category/subcategory pair, **when** normalization finishes, **then** the draft is cancelled and the user is asked to restate the expense; an unusable confirmation is not sent.
12. **Given** a mixed basket with one unsplittable amount, **when** no dominant purpose is clear, **then** normalization does not invent a split or force a subcategory; the user is asked to restate the expense. Ready-to-drink rose milk, raw meat for cooking, and hair-colour products follow their item-specific taxonomy categories.

### Integration-test scenarios

- POST a text payload and assert the draft/normalization path executes once.
- Replay the payload and assert it has no second transaction effect.
- POST an interactive confirmation and assert it precedes the ordinary-message route.
- Route two new messages on the same day and assert one background aggregation; fail that work and assert a later message can retry it.
- Route audio, confirm its words, and assert the resulting extraction still requires the usual expense confirmation. Discard audio and assert no extraction or transaction.
- Transcribe mixed-language audio and assert its original wording is staged and reviewed, while extraction produces configured English classification labels.
- Normalize a message with an amount but no subcategory and assert it is cancelled with a retry instruction, without an active extraction or confirmation button.

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

Explicit recurring-commitment payments (FIN-EPIC-006) may also create expense transactions without a capture draft. Their separate origin and payment reference preserve traceability; captured transactions still require their source draft and keep the capture confirmation invariants. The portal has no general raw-expense creation endpoint: web capture requires a persisted draft/extraction and explicit confirmation.

## FIN-001/FIN-002 — Web expense capture

1. Activity offers **Add expense** for today and **Add missing expense** for a selected past day, including days with existing expenses. Future dates cannot start capture. The action opens Add expense with a visible selected-date context and defaults to Enter manually; Describe with AI is an optional choice. Ask AI also offers Add expense independently of the calendar.
2. AI browser preparation uses the authenticated active real/demo profile, a UUID request ID, bounded prior user statements, and the selected date. No caller-supplied owner or executable SQL is accepted. Canonical taxonomy and owned active merchant/account names and aliases inform extraction. Beneficiaries are not assigned to expense rows.
3. AI preparation extracts one actual ordinary expense. Missing positive amount, invalid/missing category pair, ambiguous purpose/names, and future extracted dates ask for clarification rather than creating a confirmable preview. Missing merchant/account is optional. Follow-ups retain the earlier user statements; there is no forced one-question limit or invented split.
4. The selected date defaults an unspecified date; relative dates use actual profile-local today. An explicit date appears in the preview for user review. Preview shows currency, rounded amount, date, merchant, category/subcategory, and optional account.
5. Preparation retains source evidence in a `WEB_APP` draft. Complete previews have one ACTIVE extraction. Incomplete attempts retain a CANCELLED draft with no extraction. The preview never writes a financial transaction or creates saved names.
6. Record expense confirms an owned web extraction through the shared confirmation/reference/transaction writer. Its transaction retains the source draft, taxonomy spending nature, reference ownership, recurring matching and dirty calendar-date handling; current commitments refresh. It does not consume a WhatsApp date handoff or send a WhatsApp acknowledgement.
7. Duplicate confirmation returns the recorded date without another write. Foreign or WhatsApp extraction IDs are inaccessible. Cancel closes the preview; Edit closes it before the next preparation. A cancelled preview cannot later record. AI preparation retries reuse the request ID and cached response, without another model call or debit.
8. AI preparation and follow-ups use the existing money-chat model, timeouts, usage reservations, profile wallet and global daily budget. Confirm/cancel do not call the model or charge credits. Offline sending is disabled, failed messages remain editable, and ambiguous confirmation failures can retry the same extraction. Profile switch/unmount discards the browser capture state and ignores late replies.
9. Successful recording refreshes calendar, recent activity and commitment reads. View expense opens the persisted expense's date, including an explicit date different from the original selection. Questions continue through the read-only query tools; writes require the separate confirmation control.

Verification: `WebExpenseCaptureServiceTest`, `WebExpenseCaptureControllerTest`, `ExpenseConfirmationCommandHandlerTest`, `WebExpenseCapturePostgresTest`, and mocked desktop/mobile browser capture scenarios. Provider quality still needs a configured provider; deterministic tests control extraction output.

### Manual entry acceptance criteria

1. Enter manually is the default in the frontend, with positive amount, editable selected date, required category/subcategory, and optional searchable owned merchant/account choices. New explicit names are allowed; beneficiaries remain outside expense assignment.
2. Manual preparation validates profile-local past/current dates, rounded positive amounts within storage bounds, and valid taxonomy pairs. It resolves full canonical names before unambiguous aliases. Invalid details or ambiguous aliases do not produce an extraction or transaction.
3. Manual capture remains usable with exhausted/paused credits, missing model/tariff configuration and a failed credits endpoint. Preparation, saved-name lookup, confirmation and cancellation make no model or wallet calls.
4. Preparation retains a server preview; only Record expense writes through the shared confirmation path. Preview creates no references. Identical concurrent request retries return one extraction; changed details under one request ID conflict; cancelled previews cannot be revived. Foreign/profile-switched extraction IDs are inaccessible. Recording retries create one transaction.
5. Edit cancels the old preview and restores entered form values; a replacement uses a new request ID. Either entry method can be selected before preparing a preview. Successful recording refreshes activity/calendar/commitments and View expense opens the actual recorded date.

Verification: `ManualExpenseCaptureServiceTest`, manual controller scenarios in `WebExpenseCaptureControllerTest`, real PostgreSQL concurrent manual preparation/conflict/cancel/confirmation scenarios in `WebExpenseCapturePostgresTest`, and frontend desktop/mobile manual scenarios with AI disabled or credits unavailable. Interface: [free manual preparation](../../contracts/expenses.md#free-manual-expense-preparation).
