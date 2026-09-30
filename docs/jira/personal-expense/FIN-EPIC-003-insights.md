# FIN-EPIC-003 — Personal spending calendar and insights

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |
| Goal | Explain recorded spending without claiming information the user has not provided |

## Cross-stack ownership

| Layer | Implemented responsibility |
| --- | --- |
| `FinancialTransactionCalendarService` | Aggregates visible expenses by selected month and produces the per-day transaction count, total spend, and intensity. |
| `FinancialTransactionListService` | Returns recent or selected-date expense rows, ownership-scoped filter summary, and cursor metadata. |
| `PendingActionContextService` | Creates the short-lived selected-date context consumed by the next eligible WhatsApp text expense. |
| `MonthlyFinancialSnapshotService` | Maintains the canonical live monthly commitment projection; generated spending-story snapshots have been retired. |
| `frontend/src/App.svelte` | Owns selected month, URL `?month=YYYY-MM`, online refresh, and month/profile-local cache. |
| `frontend/src/Home.svelte` | Renders calendar intensity, the combined monthly overview, recent/date activity tabs, missing-transaction handoff, and live monthly commitment details. |

Future optional planning-data enrichments for this feed follow [FIN-ARCH-001 — Composable story enrichment](FIN-ARCH-001-story-enrichment.md). They must remain relevant to each story's evidence and must not make an optional input a prerequisite for normal expense insights.

## FIN-009 — Browse a monthly spending calendar

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. **Given** a valid `YYYY-MM` month, **when** the calendar is requested, **then** it returns profile currency/timezone, monthly totals, and one aggregate per recorded day.
2. **Given** no expenses for a day, **when** it is selected in the portal, **then** the user can start a time-limited WhatsApp handoff for that past date; future dates are rejected.
3. **Given** a month/date expense query, **when** it is requested, **then** only visible expenses belonging to the active profile are returned with filter summary and cursor metadata.
4. **Given** an invalid/missing calendar month, **when** requested, **then** it returns `400 INVALID_MONTH`.

### Portal behavior

1. The selected month comes from `?month=YYYY-MM` or defaults to the current local month. Changing it updates browser history and reloads calendar, recent activity, and monthly commitment.
2. Each day button uses the API-provided `intensity` (0–4) as its visual spend-depth class. The frontend does not calculate or reinterpret intensity from transaction amounts.
3. The month summary displays API totals: `totalSpend`, `transactionCount`, and `highestSpend`.
4. **Recent** displays the latest five records loaded for the selected month. Selecting a calendar day switches to the date activity tab and requests up to 50 records for that selected date.
5. The activity panel initially shows five items and can expand to all returned items. It is a presentation limit, not backend pagination.
6. A future day cannot be selected. An empty past day can open the missing-transaction flow, which creates a date context and offers the returned WhatsApp URL.
7. Editing or deleting an item refreshes calendar, recent activity, and monthly commitment so the views converge on the updated record. A dedicated calendar job drains dirty aggregate dates independently of story generation.

### Integration-test scenarios

- Seed expenses across days and assert aggregate totals, highest day, and timezone-aware dates.
- Request a valid missing-date context, then assert a future date and invalid timezone fail.
- Verify a session cannot read another profile's calendar or expense list.
- Render a fixture with intensities 0–4 and assert each calendar day uses the matching visual class without recalculating it.
- Select a populated day and assert a date-scoped expense request; select an empty past day and assert context creation plus WhatsApp handoff; assert future days are disabled.
- Edit/delete a displayed item and assert calendar, recent activity, and commitment fetches are requested again.

## FIN-010 — Generated spending stories (retired)

Generated spending-story cards, their publication history, rule evaluation, scheduler, and database tables have been removed. Home combines recorded expenses and the live Monthly Commitment described by FIN-018 in one overview. Recorded expenses and planned commitments have separate labels and amounts; planned commitments are never presented as money already spent. Expense exploration is available through the V1 money chatbot, which reads owned source data rather than story snapshots.

### Verification scenario

- The monthly commitment endpoint returns exactly the live commitment, and Home has no generated spending-story carousel or Stories menu. The combined Home overview shows recorded expenses and planned commitments separately. Migration V36 removes `money_story_evidence`, `money_story`, and `money_story_snapshot`.

## V1 prototype — Conversational expense exploration

**Status:** Implemented prototype · **Scope:** Read-only recorded expenses and user-owned financial planning data

The V1 dashboard includes an **Ask about expenses** panel. It uses the existing configured AI provider with a general `query_expenses` tool plus financial-record, monthly-plan and scenario tools; a scope check rejects unrelated questions, while no question-specific intent routing or separate prompt per scenario is used. The same catalog/executor is exposed through a private MCP endpoint. The portal uses in-process tool dispatch to avoid a loopback HTTP request. See [expense chat contract](../../contracts/expense-chat.md).

### Acceptance criteria

1. Only after profile verification, the V1 dashboard offers a compact money-chat panel with example questions, typed questions, loading/error/offline states, and a new-chat action. V2 is unchanged.
2. Questions can dynamically combine date ranges, up to two grouping dimensions, text/amount filters, ordering and detail/summary modes. Totals, category/merchant breakdowns, largest expenses and period comparisons use this same tool. Unsupported data or query operations are explained or clarified rather than fabricated.
3. Every query is scoped to the authenticated active profile, excludes deleted expenses, binds values and restricts SQL identifiers/operators to an allowlist. Caller/model-supplied SQL and ownership fields are rejected. The prototype cannot create, update or delete any record.
4. Replies retain recent conversation context and include expandable query evidence with date boundaries, filters, count, total and returned rows. A row limit never changes the reported complete matching total/count; truncation is explicit.
5. **New chat** preserves the current conversation in a horizontally scrollable **Recent chats** strip below the chat header. Selecting a conversation restores its messages, evidence, unsent draft and original month context; follow-up requests include only that conversation’s recent messages. Conversations are titled from their first question. Closing/reopening the panel retains them. New-chat and conversation-switch controls are disabled while a response is pending. The browser saves each conversation to a profile-owned database row and reloads recent conversations after a page refresh or later sign-in; real and demo profiles remain separate. Drafts are saved after a short idle delay. Save/load failures are visible. Transient request errors are not saved. Unmount aborts the browser request and ignores late replies. History is not saved to browser storage.
6. Missing provider configuration, provider/database failure, or exhausted query limits produce an actionable error. Failed questions remain available to retry. Unauthenticated requests never reach the model or expense tool.
7. The assistant reads owned loan, mutual-fund, stock, commitment, savings, credit-card and account-label records through bounded server queries. It distinguishes scheduled investment transactions from confirmed investments, stored holdings from live market value, and original loan principal from outstanding balance.
8. For the current and next month it reads the canonical commitment projection without persisting a new snapshot. With a saved exact regular monthly salary estimate, backend arithmetic returns the projected surplus/shortfall; range, missing and irregular salary do not yield a numeric comparison. The assistant does not expose the saved salary amount directly, although a user can infer it from total plus difference.
9. The assistant can compute a next-month what-if by reducing selected planned investment, savings, or recurring-commitment sources. A scenario leaves records unchanged, preserves loan and credit-card bill amounts, and labels unverified flexibility and remaining savings targets. It never describes a missed payment as safe merely because the UI supports Skip.
10. No account balance, confirmed salary receipt, live market valuation, sale proceeds, or payment-deferral permission is supported. Missing data remains explicit.
11. The assistant classifies the latest question in conversation context before answering. Questions outside the user's own money information receive a polite refusal with no financial tool call or unrelated answer. Ambiguous classifications also decline. In-scope questions with unavailable data explain the limitation.

### Verification scenarios

- Model chooses a composable query, receives its result and produces an answer with evidence; a follow-up includes recent context without introducing intent routing.
- Reject an unrelated question before tool use, and fail closed when scope classification is ambiguous.
- Real PostgreSQL queries isolate two owners, exclude deleted rows, preserve full totals under truncation, and combine filters/grouping correctly.
- Reject unknown fields/operators, oversized/invalid ranges, ownership arguments and forged system-role history; bound the model loop.
- Reject unauthenticated chat/MCP calls and disallowed MCP origins; use the selected authenticated profile.
- PostgreSQL tests verify stored messages, evidence and draft survive a fresh store instance, isolate profiles and reject invalid roles.
- Browser tests cover starter questions, follow-up payloads, evidence, failures/retry, new-chat preservation, refresh restoration, reopening conversations with isolated follow-up context and drafts, pending-response controls, mobile strip overflow, offline state and profile-switch isolation.

## Usage-based money-chat credits

**Status:** Implemented · **Scope:** V1 chat model calls; see [credit contract](../../contracts/ai-credits.md).

### Acceptance criteria

1. Each real/demo profile starts at zero and needs a manual grant. Every chat provider call, including scope classification and refusals, reserves credits before execution and settles from reported input/cached/output usage at the snapshotted configured tariff. Six-decimal arithmetic excludes cached input from the uncached count.
2. Wallet and UTC daily-budget reservations are database-atomic across concurrent requests and instances. Shared daily allowance, per-question maximum, per-user rolling rate limit, access pause and global switch reject work before the next provider call.
3. Replaying the same request ID and body cannot call the model or debit twice. Changed content under the same ID is rejected. Success and failure outcomes are persisted; completed calls remain charged when later work fails.
4. Missing/uncertain usage retains a durable hold and blocks new questions until verified reconciliation. A super admin can settle a stale reservation with an audited cost/note. A crash cannot make reserved usage free.
5. Chat displays available/held credits and reloads them after attempts. Exhaustion, pause and failed balance loads disable sending without hiding records or conversations. Grants become usable after refresh; unknown network retries reuse the request ID while the panel remains mounted.
6. Profile settings expose manual grants, user pause/resume and per-user activity to super admins. Grants are idempotent and audited; admin access depends only on the authenticated profile’s `SUPER_ADMIN` role, regardless of channel or portal-enabled status. Credit UI is remounted on profile switches.
7. Expense capture, audio transcription and monthly commitment presentation remain outside chat credits and its daily budget. No automatic refill or purchases are introduced. Missing tariff or shared daily-budget configuration fails closed, including on initial rollout. The shared daily budget defaults to zero and must be explicitly selected; no 1,000-credit allowance is assumed.

### Verification scenarios

- Exercise PostgreSQL races for the same wallet and shared budget, input/cache/output settlement, duplicate grants and requests, pause/rate/question limits, UTC rollover, and restart recovery with held usage.
- Verify authenticated profile isolation and reject normal-user access to admin endpoints; allow `SUPER_ADMIN` regardless of channel or portal-enabled status.
- Verify scope and answer calls both reserve/settle, and no model call occurs with rejected credits or a cached answer.
- In the browser, verify exhaustion preserves history, replenishment enables sending, network retry retains its ID, failed balance loads disable sending, and admin grants/pause update the selected user.
