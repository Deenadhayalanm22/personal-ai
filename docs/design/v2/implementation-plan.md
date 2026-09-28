# UI v2 — 15-stage implementation plan

## Product core
Help me understand what happened with my money, what changed, and what needs attention. The primary screen is a calm vertical journey, with monthly context above it. Routine actions happen beside their dated occurrence; full management stays in Money. A separate conversation surface will accommodate the future assistant.

One stage per working session, followed by review. These are checkpoints, not a scheduled automation or guaranteed duration. Stages 1–3 are implemented with samples. Stage 4 and stages 5–15 are planned.

| Stage | Deliverable | Verification gate |
| --- | --- | --- |
| 01 | Isolated vertical sample journey; monthly context; expandable dates; upcoming/overdue items; inline record/skip; confirmed sample capture | Desktop/mobile flows, separate financial measures, reload reset, no backend calls or v1 changes |
| 02 | Review hierarchy across busy, quiet, overdue and first-use days; compact story previews; restrained SVG details | User can find date, amount, due action and evidence without interpreting art; no uncontrolled card growth |
| 03 | Month/date navigation, stable day/detail URLs, back and scroll restoration; bounded event previews and quiet-date gaps | Month boundaries, timezones, keyboard/touch, drafts and deep links; one composer with explicit date |
| 04 | Separate deployment, authentication and server-enforced owner-only access | Unauthorized access rejected; profile/logout clears state; cookies/origins verified; independent rollback |
| 05 | Owned daily-summary read composition, monthly projection with independent period/freshness, batched pagination and caching | No request per scroll tick; bounded requests, correct invalidation, stale/offline/error states; missing data is not zero |
| 06 | Typed due/upcoming/overdue occurrence read model and shared action eligibility | One occurrence identity across timeline and Money; source-specific rules; no frontend reconstruction of production financial schedules |
| 07 | Published story reader, evidence and coverage period; stable assistant context references | Story period distinct from publication date; verified evidence; long stories do not overwhelm home |
| 08 | Historical publications and grouped same-day versions; agreed history APIs | Original narrative/evidence retained; original vs latest explicit; no fabricated historic projection from current data |
| 09 | Late entries, edits/deletions and cross-date corrections | Effective vs recorded date; affected periods refreshed; privacy/deletion semantics; prior publications clearly labelled |
| 10 | Live conversational capture, confirmation, WhatsApp handoff and transaction management | Questions vs records, type/date clarification, idempotency, expired proposals, cancel/retry and duplicate handling |
| 11 | Live commitments and savings actions in dated cards using shared domain forms/commands | Due/early/extra/skip rules; actual vs planned amounts; setting aside money is not paying a bill; no duplicate expense |
| 12 | Live loan record/skip actions; history, final EMI, closure and restructure in Money | Fixed due amount/date rules, optional bank penalty, schedule extension, last EMI and repeated requests; verified closure |
| 13 | Live investment occurrence actions and remaining Money parity | Fund amount/units/derived NAV; stock units/price and distinct command; contributions vs valuation; credit-card evidence/configuration; source-specific restrictions |
| 14 | Contextual read-only assistant prototype plus accessibility/performance hardening | Date/period/version/evidence scope, ownership, uncertainty and return context; separate conversation surface; measured loading budgets |
| 15 | Complete private-user verification and rollout decision | Capture → activity → story/evidence → history/correction → due/skip → loan/investment/savings → assistant context; v1 regression, security, slow network, accessibility and rollback |

## Stage 1 — vertical revision implemented, awaiting review
The sample month is September 2026; demo today is 21 September. A compact month card separates recorded expenses from planned commitments, opens a breakdown, and identifies pending items. It is not a bank balance or a complete forecast.

A single quiet Tell us entry defaults to demo today. Browsing history does not change its date. An explicit Add something action on a past date targets that day; switching capture date resets its unsaved message. A limited local parser previews editable sample expenses before confirmation. Questions and unsupported payment types do not become expenses.

Date groups cover 15–22 September, newest first. Today opens expanded, tomorrow is a collapsed labelled plan, and past dates remain available. Multiple groups can be expanded. A compact overdue link jumps to the original due date instead of creating a second actionable occurrence.

Today offers commitment, investment and savings examples; 19 September contains an overdue loan. Record and Skip open source-specific inline confirmations. Loan amount/date are fixed; investment collects units; savings explicitly sets money aside. Completed controls disappear, activity and monthly figures update, and repeated decisions are rejected. This is all temporary sample state. Loan schedule extension is explanatory only; no real schedule is recomputed.

Stories and evidence remain readable beside activity. Sample stories do not regenerate after edits. One small semicircular sky around a traveller follows real device-local time independently of demo dates. No city or second horizontal navigation is added. Journey/Money remain the only bottom destinations; management modules are labelled previews.

Review: Can you scan what happened, find what needs attention, record an outcome without losing your place, and distinguish actual activity from future plans?

## Stage 2 — hierarchy and first-use pass implemented, awaiting review
Collapsed date cards show the key amount and meaning: spending on busy days, contribution or repayment on other days, and planned or overdue amount on the relevant date. Quiet dates say no activity was recorded; confirmed new sample expenses replace that message. A skipped overdue occurrence no longer appears as due.

Expanded story previews lead the date card, with a short visible explanation and an evidence action. New sample actions label the original story as unchanged. Routine scheduled items follow in a compact list; full source-specific forms still open inline. Small tree, bridge and savings SVGs stay decorative.

A switchable first-use preview shows an honest blank month, a single capture invitation and no fabricated past journey. Confirming a sample expense reveals its amount and row, with no invented AI story. The footer switches back to the populated sample. This is a review scenario, not saved onboarding state.

Review: Without opening every date, can you identify the busy spending day, the overdue amount, a genuinely quiet day and today’s due tasks? In the first-use preview, is the next action clear without pretending a financial history exists?

## Stage 3 — navigation implemented
Sample date, month, story and source-detail URLs now support direct load, reload and browser Back. Adjacent quiet dates form a compact stretch, and busy dates preview three activity rows before Show all. August and October navigation explicitly says sample data is unavailable; September remains the only populated fixture month.

## Stage 4 — private access pending

The prototype currently has no login, server-side access gate or deployment configuration. Keep this stage inside the v2 folder when implemented; do not add v2 routes or configuration to the shared backend or v1 frontend while the prototype is independent.

## Integration boundaries
The prototype has no API, login or real data. Keep existing frontend and backend unchanged. Live integration must update the owning epics/contracts and reuse domain commands across Journey, Money and future assistant confirmations. Do not duplicate schedule/accounting logic in the feed. Fetch date summaries in batches; load bounded details on demand and cache by owner/period. Monthly projection and story history have separate scopes.

The assistant is not automatically launched by this plan. Stage 14 is a labelled read-only prototype; production AI and mutations need explicit contracts. General conversation is independent of the day currently visible. News, city-building, elaborate walking animations and expanded Easter eggs remain deferred.

## Reference
The earlier horizontal stage 1 is preserved in [stage01-horizontal.zip](references/stage01-horizontal.zip). It is a design reference, not a second live mode. The [full-product review](full-product-layout-review.md) records placement rationale and backend gaps.
