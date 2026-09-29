# V1 money chat

Feature source: [FIN-EPIC-003](../jira/personal-expense/FIN-EPIC-003-insights.md), [FIN-EPIC-005](../jira/personal-expense/FIN-EPIC-005-planning.md), and [FIN-EPIC-006](../jira/personal-expense/FIN-EPIC-006-essential-commitments.md).

## POST /api/web/expense-chat

The path remains unchanged for V1 compatibility. Requires the `WEB_SESSION` cookie and uses the active real/demo profile from `WebAuthenticationService`. No owner ID is accepted. Request:

```json
{"message":"Next month my commitments exceed salary. What could I adjust?","month":"2026-09","history":[]}
```

`message`: nonblank, at most 2,000 characters. `month`: ISO `YYYY-MM`, used when the question has no explicit period. `history`: required, at most 12 `{role,content}` items and 24,000 characters total; roles only `user` and `assistant`. Browser holds history only in component memory and starts a fresh conversation on New chat, retains earlier chats only while the page stays open, and clears all chats on reload, sign-out or real/demo switch. History is conversation context, never financial evidence.

Response: `{answer: string, evidence: Evidence[]}`. Each `Evidence` is the exact bounded result of one of the four tools below. The existing expense-query result has `query`, `currency`, `matchingCount`, `matchingTotal`, `rows`, and `truncated`; its rows can be limited without changing complete matching totals. Other results carry `kind` (`records`, `plan`, or `scenario`) and their own fields. The UI shows source facts and assumptions in expandable evidence, with a side-by-side baseline/scenario table when relevant.

The model may take at most five turns and eight tool calls per request. Each provider call times out after 20 seconds with no automatic retries; SQL times out after five seconds. The service stops starting new operations after 60 seconds. Four conversations may run globally at once and one per active profile; these are process-local limits. The browser timeout is 95 seconds. No conversation is saved to the app database.

Errors follow `{code,message}`: `401 UNAUTHORIZED`, `400 INVALID_CHAT_REQUEST`, `429 CHAT_BUSY`, `422 CHAT_QUERY_LIMIT`, and `503 CHAT_NOT_CONFIGURED`/`CHAT_UNAVAILABLE`. Bad tool arguments return an error to the model for repair within the call bound. Internal database/provider details are not returned to the browser.

## Tool catalog

Canonical schemas live in `backend/src/main/resources/insights/`. The portal model loop and private MCP endpoint share the same definitions and executor. There is no intent classifier and no model-supplied SQL. The server selects known SQL and binds all filter values. All tools use the authenticated active profile, never a model-supplied owner.

### `query_expenses`

Read recorded, non-deleted expenses across a 1–366-day inclusive/exclusive range. Group by up to two of category, subcategory, merchant, account, nature, day, month or ISO weekday; combine up to eight amount/text filters; return summaries or details. Rows are limited to 1–50, while `matchingCount` and `matchingTotal` cover all matches. Joined references must belong to the same profile. This tool continues to use the existing [expense query schema](../../backend/src/main/resources/insights/expense-query-tool.json).

### `read_financial_records`

Arguments: `{module,view,id,search,limit,offset}`. Modules: `loans`, `mutual_funds`, `stocks`, `commitments`, `credit_cards`, `savings`, `accounts`. `view` is `records` or `history`; history requires an owned parent ID and is unavailable for cards and account labels. `id` may be null for records. `search` is a literal case-insensitive name substring. `limit` is 1–50; `offset` is 0–10,000. Result: `{kind:"records",module,view,currency,matchingCount,rows,truncated,nextOffset,note}`. Page when truncated; do not treat one page as the whole portfolio.

Investment record totals count only `CONFIRMED` transactions. Scheduled SIPs and stock plans are future allocations. These records expose saved invested amounts/units and plans, with no live market valuation, sale proceeds or available cash. Original loan principal is not the remaining loan balance. Account labels are not balances. Savings marked `SAVED` reflect user-recorded amounts set aside, not bank reconciliation. Do not sum these module records into the canonical monthly plan, which already contains their eligible planned sources.

### `read_monthly_plan`

Argument: `{month:"YYYY-MM"}`, limited to current or next month in the active profile timezone. It runs the same source calculation as `monthly_financial_snapshot` without writing a snapshot. Result includes `kind:"plan"`, `month`, `currency`, `baselineTotal`, `proposedTotal`, `reduction` (zero), buckets and individually keyed source items with due date, amount and condition. Loan EMIs, active investment plans, recurring commitments, credit-card bill projections and earmarked savings use the existing deterministic projection rules. The total is **intended monthly commitment**, not unpaid balance or a complete cash-flow forecast; historic expense rows and card charges must not be added again.

`incomeStatus` is `EXACT_MONTHLY_ESTIMATE`, `RANGE_ONLY`, `IRREGULAR_INCOME`, or `NOT_SHARED`. Only an exact regular monthly salary estimate produces `baselineAfterIncome = salary estimate − baselineTotal`; negative means projected shortfall. The saved salary amount is never directly returned, but **can be inferred by adding the disclosed total and difference**. A range, absent salary, or irregular income returns null, never a guessed midpoint. The estimate is not a confirmed deposit or bank balance.

### `simulate_monthly_plan`

Arguments: `{month:"YYYY-MM",adjustments:[{"sourceKey":"MUTUAL_FUND_SIP:2:2026-10-12","newAmount":5000}]}`. It supports next month only, after inspecting the fresh plan. There may be 1–20 distinct owned source keys; each new amount must be nonnegative, at most the current source amount, and have at most two decimals. Unknown, duplicate and other-profile keys are rejected. Loan and credit-card bill sources cannot be reduced. Planned investing, earmarked savings and recurring commitments can be changed **for calculation only**, with conditional warnings. The response includes `kind:"scenario"`, original/revised totals, reduction, original/revised salary difference when available, all source amounts and limitations. It does not persist edits, mark payments, skip occurrences or verify lender/provider permission. Reduced savings leaves the underlying bill intact. A flexible schedule or application Skip control alone does not establish safety to defer a commitment.

The plan omits unrecorded living costs or obligations. Answers must explain this when evaluating affordability and cannot claim a guaranteed safe adjustment.

## Private MCP endpoint

`POST /api/web/expense-chat/mcp` supports stateless MCP Streamable HTTP JSON responses using protocol `2025-06-18`. It requires the same portal cookie. `initialize`, `ping`, `tools/list` and `tools/call` are supported; notifications return HTTP 202. GET/SSE, sessions and server-initiated requests are unsupported. An Origin header, if provided, must match `app.cors.allowed-origins` or the origin of `app.web.base-url`; an optional `MCP-Protocol-Version` must match. `tools/list` exposes four read-only annotated tools. `tools/call` returns text content and structuredContent, or `isError:true` for invalid arguments. JSON-RPC invalid requests/methods/params return -32600/-32601/-32602; database failure returns -32603 without internals. Authentication failure remains HTTP 401.

The V1 portal calls the same catalog/executor in process to avoid loopback HTTP. Third-party OAuth/Claude/ChatGPT registration is outside this embedded prototype.

## Run and verify

Use the existing `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `OPENAI_MODEL`. Optional `EXPENSE_CHAT_MODEL` overrides the model for this chat. The key stays on the backend. The configured provider must support tool calls. Run the backend and V1 frontend as usual; sign in, open **Ask about your money**, and try: “Where did I spend more?”, “Show my loans and SIPs”, “Can I cover next month’s commitments?”, then “Keep my SIP unchanged; what else could I adjust?” Check each expandable data query. Live model quality and latency require a configured provider; deterministic acceptance tests use controlled model output.

Backend tests: `cd backend && ./mvnw -Dtest=ExpenseQueryToolTest,ExpenseChatServiceTest,ExpenseChatControllerTest,OpenAiExpenseChatModelTest,MonthlyPlanningToolTest test`. The SDK test uses a local simulated provider. PostgreSQL coverage: set `EXPENSE_CHAT_TEST_DB_URL`, `EXPENSE_CHAT_TEST_DB_USER`, and `EXPENSE_CHAT_TEST_DB_PASSWORD` for a disposable PostgreSQL instance, then run `./mvnw -Dtest=ExpenseQueryPostgresTest test`; it creates/migrates/drops only a unique `chat_test_*` schema. Browser coverage: from `frontend`, run `E2E_EXTERNAL_SERVERS=1 npx playwright test e2e/expense-chat.spec.js` with the local V1 preview at port 4173; these tests mock the API.

Data sent to the configured model provider includes recent conversation, profile currency/timezone and bounded tool results. The exact salary field is not sent directly; the derived gap exposes enough information to infer it. The application does not persist history; provider retention follows its account policy. OpenAI request storage is disabled with `store=false`.
