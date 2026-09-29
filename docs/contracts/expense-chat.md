# V1 expense chat prototype

Feature source: [FIN-EPIC-003](../jira/personal-expense/FIN-EPIC-003-insights.md).

## POST /api/web/expense-chat

Requires the existing `WEB_SESSION` cookie. Resolves real/demo profile through `WebAuthenticationService`; no user ID is accepted. Request:

```json
{"message":"Where did my money go?","month":"2026-09","history":[]}
```

`message`: nonblank, maximum 2,000 characters. `month`: ISO YearMonth, used for unspecified periods. Explicit “this month” uses the current date in the profile timezone. `history`: required array, up to 12 `{role,content}` entries; roles only `user` or `assistant`, at most 12,000 characters each / 24,000 total. History is conversational context, not authoritative financial evidence. The server does not persist it.

Response: `{answer: string, evidence: ExpenseQueryResult[]}`. Each result contains:

- `query`: the executed query arguments below.
- `currency`: profile currency.
- `matchingCount`, `matchingTotal`: complete matching record count and amount, before row limits.
- `rows`: at most `limit` detail or grouped rows.
- `truncated`: more result rows exist than returned.

Model chooses queries from one tool schema, receives results, then responds. Maximum four model calls and six tool executions per request; each provider call has a 20-second timeout and no automatic retries. Four concurrent conversations globally, one request per active profile. Each SQL statement has a five-second timeout; summary and rows share a repeatable-read transaction. No additional model/tool operation starts after the 60-second turn budget; an already-running operation finishes within its own timeout. These are prototype process-local concurrency limits, not distributed rate limiting. Browser timeout is 95 seconds.

Errors follow `{code,message}`: `401 UNAUTHORIZED`; `400 INVALID_CHAT_REQUEST`; `429 CHAT_BUSY`; `422 CHAT_QUERY_LIMIT`; `503 CHAT_NOT_CONFIGURED` or `CHAT_UNAVAILABLE`. Tool argument errors are returned to the model for repair within the same bounded loop. Internal SQL/provider errors and credentials are not returned to users.

## General expense query tool

Canonical schema: `backend/src/main/resources/insights/expense-query-tool.json`.

Example:

```json
{"startDate":"2026-09-01","endDate":"2026-10-01","mode":"summary","groupBy":["category"],"filters":[{"field":"category","operator":"ne","value":"Rent"}],"orderBy":"amount_desc","limit":20}
```

- Dates: inclusive start, exclusive end, 1–366 days. Date values refer to recorded local expense dates.
- `mode`: `summary` or `details`. Details requires an empty `groupBy`.
- `groupBy`: up to two distinct dimensions from category, subcategory, merchant, account, nature, day, month, weekday (ISO Monday=1 to Sunday=7).
- `filters`: up to eight AND conditions. Text fields support case-insensitive `eq`, `ne`, `contains` (literal substring, not SQL wildcards). Amount supports numeric `eq`, `ne`, `gte`, `lte`.
- `orderBy`: `amount_desc` sorts by total/amount, `label_asc` sorts group labels, `date_desc` sorts detail dates. In summaries, `date_desc` sorts group labels descending (use day/month for chronological sorting). In details, `label_asc` sorts category ascending.
- `limit`: 1–50 rows. Summary rows contain dimensions plus count, total, average and largest. Details contain id, day, amount, category, subcategory, merchant and account.

PostgreSQL performs aggregation before limiting results. Unknown category/subcategory become `Uncategorized`, merchant/account `Unknown`, and nature `UNKNOWN`. Profile ownership and deleted-row exclusion are mandatory server predicates, not model arguments. Joined reference ownership is also checked. No raw SQL, arbitrary tables, write operations, draft messages, credentials or unrelated personal data are exposed. This is a composable query vocabulary for expenses, **not unrestricted text-to-SQL**.

## Private MCP endpoint

`POST /api/web/expense-chat/mcp`: stateless MCP Streamable HTTP with JSON responses, protocol `2025-06-18`, authenticated by the same portal cookie. Supports JSON-RPC `initialize`, `ping`, `tools/list`, `tools/call`, and accepts notifications with HTTP 202. GET is unsupported (405); no SSE stream, sessions or server-initiated requests. If supplied, `MCP-Protocol-Version` must match. An Origin header must match `app.cors.allowed-origins` or the origin of `app.web.base-url`.

`tools/list` exposes `query_expenses` with a read-only annotation and its input schema. `tools/call` accepts `{name,arguments}`, returns MCP text content and structuredContent, or `isError: true` for invalid queries. JSON-RPC invalid requests/methods/params return -32600/-32601/-32602; database failure returns -32603 without internal details. Authentication errors remain HTTP 401.

The portal model loop uses the **same catalog and executor in process**, not HTTP MCP round trips. The private endpoint is available for authenticated protocol testing; public Claude/ChatGPT integration and OAuth are outside this prototype.

## Configuration and trying it

Use existing `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `OPENAI_MODEL`. Optionally set `EXPENSE_CHAT_MODEL` independently; it defaults to the existing model (repository default `gpt-4.1-mini`). No frontend key or new database migration is needed. Tool calling must be supported by the configured model/provider.

Run the backend and V1 `frontend` as usual, sign in, and click **Ask about expenses**. Try “Where did my money go?”, then “Exclude rent”, “Show the largest five expenses” or “Compare with the previous month”. Check expandable evidence against recorded data. No live-model output is treated as a deterministic acceptance assertion.

Data sent to the configured AI provider includes the recent conversation, profile currency/timezone, and bounded expense query results. Application-side history is memory-only; provider retention follows that provider/account policy. OpenAI request storage is disabled with `store=false`.

## Verification commands

- Backend unit/transport tests: `cd backend && ./mvnw -Dtest=ExpenseQueryToolTest,ExpenseChatServiceTest,ExpenseChatControllerTest,OpenAiExpenseChatModelTest test` (the SDK transport test uses a local HTTP server, no provider credentials).
- PostgreSQL acceptance tests: set `EXPENSE_CHAT_TEST_DB_URL`, `EXPENSE_CHAT_TEST_DB_USER`, `EXPENSE_CHAT_TEST_DB_PASSWORD` to a disposable local PostgreSQL instance and run `./mvnw -Dtest=ExpenseQueryPostgresTest test`. This test creates/migrates/drops only a unique `chat_test_*` schema; it does not clean existing schemas. Without the explicit URL it is skipped.
- With a local V1 preview on port 4173: `cd frontend && E2E_EXTERNAL_SERVERS=1 npx playwright test e2e/expense-chat.spec.js`. These browser tests use controlled API fixtures, not a live model.
