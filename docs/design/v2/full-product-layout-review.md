# Money Stories v2 full product layout review

Status: vertical direction approved for the stage 1 sample prototype. Updated after user review to include routine inline Record/Skip confirmations. This is a specification/code walkthrough, not completed usability testing or load testing. Live integration remains future work; see the current 15-stage plan and FIN-EPIC-007.

## Decision

Recommend Journey as a layered home: a compact monthly position, one conversational entry, and a vertically grouped history of meaningful daily activity and published stories. Keep Money as the management destination and Profile as a header entry. Treat day focus as a detail route reached from the journey, search or calendar—not a competing horizontal home mode. Provide a dedicated conversation surface when the assistant exists. Do not put all product behavior inside date cards.

This recommendation is based on the feature placement matrix below. The daily feed is preferable for discovery; a focused detail remains preferable for investigation and editing. Monthly planning and assistant conversations have different time scopes from a day and need independent owners. The direction reduces foreseeable structural rework but cannot guarantee that untested future requirements will never require changes.

## Evidence and priority

Reviewed the active Personal Expense initiative and epics 001–007, FIN-ARCH-001, contracts for expenses, stories, authentication, reference cleanup, recurring commitments, credit cards, loans, funds and stocks; current frontend loading/navigation and the story-service composition; existing Gherkin/Playwright feature coverage; prior design notes and the saved product vision.

Primary sources:

- [Active initiative](../../jira/personal-expense/INIT-002-personal-expense.md)
- [Capture](../../jira/personal-expense/FIN-EPIC-001-capture.md), [correctness](../../jira/personal-expense/FIN-EPIC-002-correctness.md), [insights](../../jira/personal-expense/FIN-EPIC-003-insights.md), [portal](../../jira/personal-expense/FIN-EPIC-004-portal-and-references.md)
- [Loans and investment planning](../../jira/personal-expense/FIN-EPIC-005-planning.md), [commitments and savings](../../jira/personal-expense/FIN-EPIC-006-essential-commitments.md), [story enrichment](../../jira/personal-expense/FIN-ARCH-001-story-enrichment.md)
- [Story contract](../../contracts/money-stories.md), [expense contract](../../contracts/expenses.md), [authentication](../../contracts/authentication.md)
- [Product vision](../../product/Open_Money_Stories_BRD_Vision_Mission.docx)
- [Existing feature coverage map](../../../frontend/e2e/features/README.md)

The vision emphasizes conversation, verified facts, explainable stories, historical continuity, and calm presentation. Some older vision/design statements are superseded: investments and loans now exist; expense edits happen in the portal; direct app conversational creation is still a future integration. Some epic status labels also lag their detailed criteria and executable scenarios. Existing contracts/code and active acceptance criteria take precedence over old design prose; a documented proposed scenario is not proof of a shipped feature.

## Product responsibilities

1. Understand current position: recorded spending and known commitments, with their scope and freshness.
2. Discover change: compare recent days, understand significant updates, open a supported story.
3. Explain and repair: inspect evidence, confirm capture, correct records and understand revisions.
4. Plan and maintain: manage upcoming obligations, savings, loans and investment holdings.
5. Converse: ask questions across dates or domains and propose changes with confirmation.

These responsibilities are not all chronological. A loan lasts years, a story may cover a month, a statement spans months, and a conversation may compare periods. A single date cannot be the universal container.

## Proposed placement

### Journey home

- Header: identity/profile. Month selector plus compact monthly position, showing recorded spend separately from known planned commitments; open the monthly story/detail for breakdown, next-month outlook and source-specific review. No net balance calculated by subtraction.
- Conversational entry: one quiet Tell us entry after the month context and before the history, defaulting explicitly to today. When the assistant is implemented it can accept a question or a record description. Answers open a conversation; proposed changes open confirmation. It must not infer a date from whichever history card happens to be on screen.
- History: newest-first date groups. Today or the most recent meaningful group can be expanded initially. Older groups show a short factual summary and relevant labelled measures; all events stay discoverable through expansion. Do not give every day spending, savings and investing numbers if those facts are absent. Do not manufacture a narrative for each day.
- Within a group: brief story update, actual activity and contextual links. Long stories open the existing reader pattern with evidence. Multiple publications for one day group into updates. A monthly story published today is labelled with its monthly coverage, not presented as a one-day result.
- Search/jump: calendar/date picker jumps to and expands a group; transaction search/filter lives in Money. Deep links return to the same group and scroll position. No simultaneous horizontal browsing mode.
- Quiet periods: compress runs of dates without activity into an honest no-recorded-activity gap; a date jump can still open a quiet day. Current reminders do not vanish merely because no expenses were recorded today.
- The small time-of-day sky can remain once by the current-day header. It reflects device time only, not financial wellbeing or the historic date. The traveller must not become a second navigation scheme or appear on every card.

Routine dated occurrences expose source-specific Record/Skip confirmations directly inside expanded groups. A compact overdue link leads to the original dated occurrence, not a duplicate actionable inbox. The month card links to the full sample list. Live integration must share commands and eligibility with Money and preserve every source’s rules. Complex editing and exceptional actions stay in their owning module.

### Money

Stable access to Transactions (including expense cleanup), Commitments (including linked savings), Loans, Investments (funds, stocks/ETF plans), and Credit-card billing configuration. Keep salary/privacy settings reachable through Profile or a clearly labelled private-planning setting, with contextual links where relevant. Plan editing, long history, exceptional actions and destructive changes belong here. Routine inline forms reuse these domains’ validations and commands.

Money is available even if there is no recent event for a holding or loan. This prevents a user having to find an old activity date just to edit a plan.

### Conversation

A scoped entry such as Ask about this story opens a conversation with visible date/period and original/latest version context. General entry is not permanently tied to one day. A mobile conversation gets its own route with room for the keyboard and transcript; desktop may use a side panel backed by the same route/state. Save draft and conversation context when opening evidence and restore them on return. Returning from conversation restores the journey position.

Do not insert every assistant answer into the financial timeline. Only confirmed domain changes and deliberate published story updates enter it. Read-only answers, proposed actions, cancelled proposals and completed actions have distinct representations. Answers cite approved evidence; backend revalidates access and freshness. Confirmation is required before a proposed write; a stale proposal must be revalidated. Voice can extend the existing confirmation concepts without becoming a new primary navigation section.

## Scenario walkthrough and placement matrix

These are design walkthroughs against documented behavior, not claims that new v2 flows have been implemented or tested.

| Scenario | Primary entry / destination | What must happen | Layout consequence |
| --- | --- | --- | --- |
| New user, no records | Journey | Show an honest empty state and one capture invitation; planning optional | Neither a long empty calendar nor fabricated daily insights |
| Expense-only user | Journey | Show spending stories without salary/investment prerequisites | Optional modules cannot dominate home |
| Daily quick check | Monthly position + recent date groups | Read current context, then discover changes | Feed better than opening dates one at a time |
| Two busy days compared | Adjacent collapsed summaries | Scan both, expand relevant details; permit expansion without collapsing an in-progress draft | Multiple-day visibility without full transaction lists |
| High-volume day | Day expansion → transaction list | Show bounded preview and full list/search/pagination | Feed card cannot contain an unlimited ledger |
| Quiet week | Journey gap / jump to date | No activity recorded, never zero-spend or success claim | Compress empty groups; preserve access to each date |
| No story yet | Activity-only day | State insufficient/no published story only where helpful | Activity does not wait for AI generation |
| WhatsApp text capture | WhatsApp → Journey | Existing draft/confirmation/idempotency; refresh after confirmed record | Channel shares domain state, not duplicate capture logic |
| WhatsApp voice | WhatsApp review | Review transcript, then extraction/confirmation | Do not bypass word review merely to fit the new UI |
| App natural-language capture | Tell us → proposal → confirmation | Future API with required fields, ambiguity, duplicate handling and explicit date | Composer independent of scrolling; current stage 1 is demo only |
| Question typed into Tell us | Conversation | Answer or request context; never save an expense just because an amount occurs | Conversation must be distinct from capture proposal |
| Mixed message or uncertain payment type | Proposal clarification | Distinguish expense, transfer, investment, loan payment; only supported actions offered | No generic save-everything button |
| Missing past expense | Selected day action → dated proposal / WhatsApp context | Explicit past date; handoff can expire; return to that day | Context follows an action, not viewport visibility |
| Edit/delete old expense | Evidence/Transactions → owned edit/delete | Refresh affected old/new periods; current data differs from prior publication | Stable day/detail routes, explicit revision states |
| View a previous story version | Day updates → story reader | Publication time, coverage period, original evidence, later correction label | Focused reader remains necessary whichever feed is chosen |
| Month review | Monthly details/story | Spending and intended commitments stay separate; breakdown accessible | Monthly context cannot be hidden inside today's card |
| Next-month outlook | Monthly story next-month card | Use actual planned source facts, label future plan | Future planning separate from actual-activity chronology |
| Historical month | Month selector/history | Distinguish latest corrected expense totals from historical published facts | Never relabel live current commitments as historical |
| Multiple commitments due | Dated groups + monthly breakdown | Inline source-specific confirmation; all sources discoverable | Overdue links original occurrence; no duplicated action |
| Weekly/daily commitment | Dated occurrence → inline action | Record/skip exact occurrence only when allowed | Cadence configuration stays in Money |
| Early flexible payment | Commitment details | Explicit early flow, actual amount/date, next date confirmation | Generic date-card Pay button would lose required semantics |
| Extra amount after payment | Commitment details/history | Positive extra and reason; retain usual planned amount | Domain-owned form, activity entry after confirmation |
| Expense matched to commitment | Story review sheet | User resolves candidate; actual expense not counted again | Capture remains fast; matching stays contextual |
| Annual insurance saving | Commitment details | Setup is not saved money; contribution is not insurance payment | Distinct planning/savings/payment labels in every surface |
| Skipped saving / eventual bill | Commitment details | Gap truthful; no automatic redistribution; savings-used allocated once | Generic progress visual must not substitute for accounting |
| Loan due / skip / final EMI | Dated occurrence → shared loan confirmation | Due gating, penalty, schedule extension, verified final closure | Routine form inline; closure/history remain domain-owned |
| Loan pre-close / restructure / mistaken deletion | Money → loan details | Preserve correct past outcomes; future schedule changes; deletion not closure | Must work without finding an old feed card |
| Mutual fund SIP / lump sum / correction | Due SIP inline; other actions in Investments | Actual amount + units; NAV derived; planned schedule distinct | Source-specific form, evidence returns to origin |
| Stock / ETF plan / skip / manual buy | Investments → stock details | Due gating, units/price, opening corrections, no supported selling flow | No generic investment payment form across domains |
| Missing NAV / price update | Investment detail | Invested amount known; current valuation may be unavailable | Do not create expense activity or healthy-tree claims from prices |
| Credit-card bill spanning months | Monthly source → billing evidence/config | Statement expenses aggregate into due projection, not another expense or payment | Multiple time scopes must be shown explicitly |
| Optional salary | Private planning/settings → relevant monthly explanation | Range qualitative; exact value only allowed deterministic context; not income/balance | Never require salary to use expense stories |
| Future emergency fund / goals / budget | Money module + relevant story enrichment | Independent opt-in contributors; no contribution without owned facts | Add a module and applicable insight, not a new home dashboard |
| General assistant question across months | Conversation | Scope explicit, compare verified periods, link to evidence | Neither daily layout alone is a conversation workspace |
| Assistant proposes record change | Conversation → domain confirmation | Show exact change and date, recheck ownership/state, prevent duplicate action | Reuse domain commands/forms, not assistant-owned financial writes |
| Assistant history | Conversation history | Retain context according to an agreed privacy policy; no auto-publication to journey | Conversation lifespan independent of date-card expansion |
| Financial news/suggestions someday | Relevant opted-in explanation | Source and date required; relevance to owned facts; no news-feed takeover | Extension point only, not a new required home block |
| Offline / backend waking / failure | Same-profile saved view | Freshness and unavailable states explicit; prevent unsafe/stale writes | Cache state separate from expanded/collapsed presentation |
| Logout / profile switch / owner-only v2 | Auth/profile | Clear scoped data/drafts/conversations; backend enforcement; deep-link safety | Separate URL alone does not isolate accounts |
| Small phone / keyboard / accessibility | All surfaces | Main facts visible; focus restored; one active composer; reduced motion | Long forms/conversations get room outside accordion cards |

## Comparison after the walkthrough

| Criterion | Single-day home | Pure vertical timeline | Layered Journey + Money + conversation |
| --- | --- | --- | --- |
| Discover change across days | Requires repeated selection | Strong | Strong |
| Inspect one day without noise | Strong | Needs good expansion/deep links | Focused detail available |
| Monthly + next-month planning | Needs a separate area | Can be buried/duplicated | Persistent owner outside chronology |
| Manage long-lived loans/holdings | Needs Money | Needs Money | Explicit stable module access |
| Ask questions spanning periods | Date scope can mislead | Chat can overwhelm cards | Independent conversation with visible context |
| Preserve historical versions | Possible with new read contract | Possible with new read contract | Same contract + dedicated reader |
| Avoid clutter as modules grow | Risk of stuffing selected day | Risk of endless cards | Domain tools separate; relevance governs feed |
| Efficient data loading | Achievable with batches/caching | Achievable with batches/caching | Same loading architecture; no UI-based guarantee |

The recommendation is the third structure. It includes a vertical journey, but rejects the idea that every feature belongs in that feed. The current single-day UI can become the reusable day detail; its components, styling, sample parser and domain-independent assets need not be thrown away.

## Actual backend gaps that placement must respect

1. `MoneyStoriesService.monthlyForWeb(user, month)` prepends `monthlyCommitment.currentFor(user)`, then loads expense stories for the requested month. Therefore the response's outer month is not sufficient to label every story as belonging to that historical month. Read each story period. A historical monthly-projection experience requires an explicit supported history contract; until then show current planning separately or unavailable for historical planning.
2. Expense-calendar data contains daily spend/count/intensity, not daily investment and savings facts. A combined day summary needs a new owned read composition; frontend must not invent it from monthly totals.
3. Latest stored expense snapshots exist, but the public story read is not a day/version archive API. New history and provenance semantics are needed. The current transaction correction path updates in place and soft-deletes, so full historical reconstruction cannot be assumed from current rows.
4. Monthly commitment behavior is live; savings/loan/fund occurrence histories have different state rules. Do not freeze a live response into a historical card without explicit snapshot semantics.
5. Live app conversational creation and a general AI assistant are not existing web contracts. A stage 1 regex demo is not evidence of those production capabilities.
6. Current-and-next planning context and all future optional enrichments must retain canonical deterministic backend ownership. No second financial calculator in the timeline or assistant.

## Loading and correctness architecture

Choose layout for user tasks; choose request shape for bounded work. A possible new journey read returns a page of date-group summaries, publication references, source types and continuation metadata. Existing monthly reads can remain separate; one network request is not the goal if it creates a large or internally chatty response.

- Batch summary retrieval, bounded by date range/page size. Avoid a database call for every rendered card.
- Fetch large evidence/transaction lists on demand and paginate them; cache owned results by query/period and revision. Expansion itself does not require a refetch of already-fresh data.
- Deduplicate in-flight reads, cancel obsolete requests, and prevent out-of-order responses replacing a newer selection.
- Invalidate affected date/month/domain data after a confirmed write; retain an explicit stale label until dependent narratives refresh.
- Published story generation is independent of scroll/swipe. Scrolling cannot trigger repeated AI generation.
- Keep business date, recording time, publication time, coverage period, due date, and latest-update time distinct. Never derive period from display position.
- Reuse source occurrence IDs and idempotency keys. A bill projection, underlying expense and savings allocation are not three copies of money spent.
- User/profile scoping and logout clearing apply to caches, pending drafts and assistant context, not just requests.
- Measure page payload, query count/time, scrolling responsiveness and load tests with sparse and busy fixtures before promising performance.

Stable UI building blocks: MonthOverview, JourneyFeed, DaySummary, DayDetail, StoryReader, EvidenceView, Composer, ActionConfirmation, MoneyModules and Conversation. Routing retains date/group/story revision plus origin/scroll anchor. Server authorization never trusts these client references.

## Design validation before changing stage 1

Keep the current prototype as the baseline. Before moving to a vertical implementation, review the recommended structure with these complete representative flows:

1. New user → WhatsApp capture → confirmed expense → first activity, no invented story.
2. Busy existing user → monthly position → compare three days → expand evidence → correct date/amount → return to original location.
3. User who has not logged expenses recently → find due SIP and insurance savings → act in owning detail → updated current plan.
4. Loan user → skipped EMI → schedule changes → later final payment/preclosure → correct historical presentation.
5. Investment user → contribution vs market valuation → missing price → correction → unchanged monthly plan where appropriate.
6. Historical user → published story → late expense/correction → original vs latest → current-month projection never mislabelled.
7. Future assistant → cross-month question → evidence → propose change → confirmation/cancel → return without losing transcript or date.
8. Mobile/offline user → cached state → open details → keyboard/draft → reconnect → no duplicate save; logout does not expose history.

Success gates: users can find current commitments without searching an old date; explain a story's period and evidence; distinguish planned/paid/saved/invested; locate an old item directly; recognize what the assistant will save before confirming; return without losing place. Compare observations and task completion, not invented scoring. No claim of usability validation until these are actually exercised with the user.

## Impact on the 15-stage plan

Do not spend stage 02 on artwork alone. Use it to settle and validate the placement map and these representative flows; refine only the small assets needed. Stage 03 can then implement agreed date/feed/detail navigation and restorable context. Stage 04 retains private access isolation. Stage 05/06 require the day-summary composition and month-scope decisions. Stage 07/08 define period/version-aware story context. Stage 10 specifies real capture and intent handling. Stages 11–13 reuse domain detail workflows. Stage 14 proves scoped assistant interaction before production AI commitments. Stage 15 runs the complete regression, privacy, accessibility and performance checks.

This review does not amend the active stage plan or approve a new API. It identifies the decisions that must be resolved before committing to the layout, and preserves the current working UI for comparison.
