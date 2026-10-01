# V1 money chat

Feature source: [FIN-EPIC-003](../jira/personal-expense/FIN-EPIC-003-insights.md), [FIN-EPIC-005](../jira/personal-expense/FIN-EPIC-005-planning.md), and [FIN-EPIC-006](../jira/personal-expense/FIN-EPIC-006-essential-commitments.md).

## POST /api/web/expense-chat

The path remains unchanged for V1 compatibility. Requires the `WEB_SESSION` cookie and uses the active real/demo profile from `WebAuthenticationService`. No owner ID is accepted. Request:

```json
{"message":"Next month my commitments exceed salary. What could I adjust?","month":"2026-09","history":[],"requestId":"123e4567-e89b-12d3-a456-426614174000"}
```

`message`: nonblank, at most 2,000 characters. `month`: ISO `YYYY-MM`, used when the question has no explicit period. `history`: required, at most 12 `{role,content}` items and 24,000 characters total; roles only `user` and `assistant`. The browser derives this bounded history from the active saved conversation. Stored evidence and other conversations are not sent with the question. History is conversation context, never financial evidence.

Response: `{answer: string, evidence: Evidence[], credits: {balance,reserved,available,paused,enabled,configured}}`. A UUID `requestId` is required for durable idempotency. Every model call is subject to [usage-based credits](ai-credits.md), including the scope check. The linked contract defines manual grants, rate/budget limits, failure accounting and additional credit error codes. Each `Evidence` is the exact bounded result of one of the four tools below. The existing expense-query result has `query`, `currency`, `matchingCount`, `matchingTotal`, `rows`, and `truncated`; its rows can be limited without changing complete matching totals. Other results carry `kind` (`records`, `plan`, or `scenario`) and their own fields. The UI shows source facts and assumptions in expandable evidence, with a side-by-side baseline/scenario table when relevant.

The backend first classifies the latest question using recent conversation context and no tools. Only questions about the active user's money information proceed to the answer loop. Unrelated or ambiguous questions receive a polite scope refusal with `evidence: []`; no financial tool is called. In-scope questions requiring unavailable information receive a plain limitation. This adds one provider call to each accepted request.

After the scope check, the answer model may take at most five turns and eight tool calls per request. Each provider call times out after 20 seconds with no automatic retries; SQL times out after five seconds. The service stops starting new operations after 60 seconds. Four conversations may run globally at once and one per active profile; these are process-local limits. The browser timeout is 95 seconds.

## Saved conversations

`GET /api/web/expense-chat/conversations` returns the 50 most recently updated conversations for the authenticated active profile, newest first. `PUT /api/web/expense-chat/conversations/{uuid}` upserts one conversation for that profile: `{id,title,month,draft,messages}`. The path UUID must equal the body ID. Title is 1–200 characters, draft at most 2,000 characters, and month is ISO `YYYY-MM`. Messages are an array of at most 100 user/assistant entries with text content up to 12,000 characters each; assistant entries may include an evidence array. The serialized messages must be at most 500,000 characters. The database stores messages and evidence as JSONB, with profile-scoped ownership and cascade deletion when the profile is removed. Both responses use `Cache-Control: no-store`. The browser saves after replies and after a short draft idle delay, and reloads on mount. Real and demo profiles have separate histories. A failed save/load is shown in the UI; transient request errors are not persisted. The browser does not store chat in local/session storage.

Saved history does not change token limits: only the current conversation's latest 12 messages, at most 24,000 characters, enter the next model request. Other saved conversations and prior evidence do not. This is application storage, not provider-side conversation state.

Errors follow `{code,message}`: `401 UNAUTHORIZED`, `400 INVALID_CHAT_REQUEST`, `429 CHAT_BUSY`, `422 CHAT_QUERY_LIMIT`, and `503 CHAT_NOT_CONFIGURED`/`CHAT_UNAVAILABLE`. Bad tool arguments return an error to the model for repair within the call bound. Internal database/provider details are not returned to the browser.

## Tool catalog

Canonical schemas live in `backend/src/main/resources/insights/`. The portal model loop and private MCP endpoint share the same definitions and executor. The scope classifier does not route questions to tools, and there is no model-supplied SQL. The server selects known SQL and binds all filter values. All tools use the authenticated active profile, never a model-supplied owner.

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

Data sent to the configured model provider includes recent active-conversation messages, profile currency/timezone and bounded tool results. The exact salary field is not sent directly; the derived gap exposes enough information to infer it. The application persists chat history in PostgreSQL; provider retention follows its account policy. OpenAI request storage is disabled with `store=false`.


## POST /api/web/expense-chat/transcribe

Requires `WEB_SESSION`; resolves the active real/demo profile before transcription. Multipart form-data contains one `audio` file (nonempty, up to 8 MiB). Recording MIME types: `audio/webm`, `audio/mp4`, `audio/ogg`, `audio/wav`, allowing codec parameters. Original filenames are ignored. The backend sends bytes to configured OpenAI `/audio/transcriptions` using `OPENAI_TRANSCRIPTION_MODEL` (default `gpt-4o-mini-transcribe`) and returns `{text: string}` with `Cache-Control: no-store`. Natural-language wording is preserved without translation or amount normalization. Trimmed transcripts must be 1–2,000 characters. No answer or financial tool is invoked, and raw audio is not saved in application records.

The microphone icon follows the Send arrow inside the composer. During recording it becomes a stop icon; animated bars, a pulsing dot and elapsed timer provide recording feedback (not measured microphone levels), with Cancel and reduced-motion support. The browser records for less than 30 seconds on HTTPS/localhost and releases microphone tracks after stop/cancel/close/profile change. Manual Stop and transcribe before 30 seconds uploads the recording. At 30 seconds the browser stops recording, discards the audio without uploading, releases microphone tracks and asks the user to re-record a shorter question. Elapsed time is checked again on manual stop to prevent delayed timer callbacks from uploading an over-limit recording. Returned text appends to any typed draft for explicit review/editing; Send separately calls the unchanged chat endpoint. Discard restores the prior typed draft. Failed uploads retain audio in memory for retry until discarded/closed. Browser timeout: 55 seconds; backend connection/read timeouts: 5/45 seconds, without provider retries.

Voice requires enabled/configured AI access and positive available credits, respecting profile pauses. Transcription is outside existing chat-token tariff and wallet ledger, like existing audio capture; provider transcription costs are separate. Process-local limits: four concurrent uploads globally, one per profile, and 15 seconds between starts per profile. Multipart limits: 8 MiB/file, 9 MiB/request.

Errors use `{code,message}`: `401 UNAUTHORIZED`; `400 INVALID_VOICE_AUDIO`; `413 VOICE_AUDIO_TOO_LARGE`; `415 VOICE_AUDIO_FORMAT`; `422 VOICE_NO_SPEECH` / `VOICE_TRANSCRIPT_TOO_LONG`; `429 VOICE_BUSY`; `503 VOICE_NOT_CONFIGURED` / `VOICE_UNAVAILABLE` / `AI_CREDITS_NOT_CONFIGURED`; `403 AI_ACCESS_PAUSED`; `402 AI_CREDITS_EXHAUSTED`. Provider details and credentials are not exposed.

Provider interface: [OpenAI speech-to-text documentation](https://developers.openai.com/api/docs/guides/speech-to-text).

## Web expense capture (the frontend)

These commands are separate from the read-only chat tool catalog and private MCP. All require `WEB_SESSION`, return `Cache-Control: no-store`, and resolve the active real/demo profile on the server. Owner IDs are never accepted. The UI enters this mode through Add expense in Ask AI or Activity. Entry defaults to the free manual form ([expenses contract](expenses.md#free-manual-expense-preparation)); Describe with AI explicitly selects the metered conversational path. AI balance failures never block switching to manual entry.

### POST /api/web/expense-chat/capture

Body: `{requestId: UUID, date: "YYYY-MM-DD", message: string, turns: string[]}`. `date` is a past/current profile-local date. Message is nonblank, at most 2,000 characters. Prior user statements are at most 12 strings, each at most 2,000 characters, at most 24,000 total. They supply conversational evidence, not authority to confirm. Relative dates use actual profile-local today; the selected date supplies an unspecified date. Only one ordinary expense is prepared at a time; investment/loan/savings commands remain domain-owned.

Response: `{status: "READY" | "NEEDS_DETAILS", answer, extractionId: number | null, preview: {amount,date,category,subcategory,merchant,account,currency}}`. Partial preview fields may be null. READY requires a positive amount after rounding to two decimals, a configured taxonomy pair, a past/current date, and no unresolved clarification. Merchant/account are optional. Owned active canonical names and aliases are read from the database; exact aliases are resolved again server-side. Up to 100 active references of each supported type and 10 aliases each enter provider context; exact server resolution still considers all owned references. Beneficiary assignment is unsupported.

Complete preparation retains a `WEB_APP` source draft and ACTIVE extraction, but no transaction or new name. Incomplete preparation retains source evidence in a CANCELLED draft without a confirmation extraction. Editing requires cancelling the old preview before preparing its replacement. Browser capture conversation state is held in memory; reload starts a new capture. Retained drafts preserve evidence; this release does not offer a draft-resume inbox.

Each preparation/follow-up uses the configured expense-chat model and existing metered credit reservation/settlement. Durable request fingerprints include a capture namespace so IDs cannot collide with question requests. Identical completed request retries replay their preview without another model call/debit. Existing credit errors and request-conflict/usage-pending behavior apply. Provider requests use the existing 20-second timeout and no retries. Process-local limits: four captures globally and one per profile; persistent credit requests enforce cross-instance/profile limits with normal chat.

Additional errors: `400 INVALID_CAPTURE_REQUEST`, `429 CHAT_BUSY`, `422 CAPTURE_AMBIGUOUS_REFERENCE`, `503 CAPTURE_UNAVAILABLE`. Malformed provider output never becomes a preview. Provider details are not returned.

### POST /api/web/expense-chat/capture/{extractionId}/confirm

No body. Uses a pessimistically locked owned `WEB_APP` extraction. ACTIVE/PENDING becomes USED/CONSUMED and writes one transaction through the shared confirmation/reference/transaction writer; current commitments refresh and the expense date is marked dirty. Returns `{status:"RECORDED",date:"YYYY-MM-DD"}`. An already-recorded extraction returns the same date without another transaction. It does not consume a WhatsApp context or emit a WhatsApp notification. No AI call/credit charge occurs.

### POST /api/web/expense-chat/capture/{extractionId}/cancel

No body. ACTIVE/PENDING becomes REJECTED/CANCELLED; returns `{status:"CANCELLED"}`. Repeating cancellation succeeds without effects. Confirming a cancelled preview or cancelling a recorded one returns `409 CAPTURE_CLOSED`. Foreign/profile-switched/WhatsApp IDs return `404 CAPTURE_NOT_FOUND` for either action. Confirmation always uses persisted preview fields, never browser/model-supplied replacement fields.
