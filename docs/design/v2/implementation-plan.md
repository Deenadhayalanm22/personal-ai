# UI v2 — 15-stage implementation plan

## Product core and decision filter

Help me understand what happened with my money, what changed, and what needs my attention through a simple personal journey.

Keep the selected date, one useful summary and its evidence easy to find. The journey is navigation, not a game or city builder. Scenery supports meaning; it must not compete with amounts, obscure missing data, or imply financial health. Every proposed addition must improve understanding, finding history, or taking a relevant action. Otherwise defer it.

One stage per working session/day, with a review gate before the next. This is sequencing, not a scheduled automation or duration estimate. Stage 15 verifies the complete integrated flow; earlier stages have their own checks. A failed gate is resolved before expanding scope.

| Stage | Deliverable | Verification gate |
| --- | --- | --- |
| 01 | Simplified isolated sample app: compact date journey, factual change summary, one supporting illustration, activity, evidence and a local Tell us confirmation prototype | Date/change/action understood without interpreting art; desktop/mobile checks; no backend calls or v1 changes |
| 02 | Validate hierarchy and visual language; small consistent tree/bridge/savings illustrations; no additional world-building | User can identify selected date, change and evidence quickly; quiet and busy days readable; touch/contrast checks |
| 03 | Reliable time navigation: months, date jump, URLs/back and return-to-date; brief optional transition only if useful | Date boundaries, timezones, keyboard/touch; content never waits for animation; reduced motion |
| 04 | Independent deployment, sessions and server-enforced owner-only access | Unauthorized/other-user rejection; origins/cookies/CORS verified; v1 regression; separate rollback |
| 05 | Read-only daily activity with loading, empty, stale, offline and error states | Owned-profile totals and dates; missing records never imply zero spending; correct retry behavior |
| 06 | Verified daily change summaries and restrained visual mapping; mixed-event priority rules | One primary summary; supporting events still discoverable; no invented balance, returns, progress or duplicated milestones |
| 07 | Published story reader and supplied evidence/actions; stable context identifiers for future assistance | Source values/order/version retained; empty/stale states explicit; return to same date; no explanatory claim without evidence |
| 08 | Historical stories and grouped same-day versions; define and implement required history APIs | Immutable original narrative/evidence; latest vs original clear; corrected facts distinguished from publication-time knowledge |
| 09 | Late entries, corrections and deletions across dates | Effective vs recorded dates explained; current view converges; prior versions labelled; ownership and deletion/privacy policy respected |
| 10 | Live conversational expense extraction/confirmation, WhatsApp handoff and searchable/editable transactions in Money | Selected/explicit dates, questions vs record changes, non-expense routing, confirmation, expiry/failures and deduplication; edits refresh affected views |
| 11 | Commitment due/upcoming/payment/skip/savings flows with one relevant attention prompt | Planned vs paid vs set aside distinct; no double counting; actions return to selected day; no dashboard full of reminders |
| 12 | Loan history and repayment/closure integration; optional bridge milestone | Verified repayment/closure; no invented completion percentage; all information accessible without illustration |
| 13 | Investments and remaining secondary tools; optional contribution-tree milestones | Contributions distinct from valuation; corrections handled; v1 management parity checklist; primary navigation stays minimal |
| 14 | Assistant-ready context contract and read-only contextual interaction prototype; accessibility/performance hardening | Selected date/story version/evidence scoped correctly; references resolvable; uncertainty explicit; no cross-user access or writes; optional motion within measured budgets |
| 15 | Full private-user end-to-end verification and rollout decision | Capture → daily change → evidence → history → correction → commitments → loans/investments → contextual explanation; ownership, slow-device/network, accessibility, v1 regression and rollback |

## Assistant preparation, not an automatic AI launch

Stage 07 identifies the date, timezone, story ID/version, publication time and evidence references the user is viewing. Stage 14 proposes and tests a permission-checked context contract plus a clearly labelled read-only prototype from a detail view. No persistent assistant panel or fabricated AI response is added in stage 1. Backend revalidates context ownership and evidence freshness; the frontend cannot authorize access. Future assistant mutations require explicit confirmation and separate agreed contracts/tests. Production AI capability and provider choice need a separately scoped decision.

## Deferred until the core earns it

Rich city-building, shops per transaction, elaborate camera/walking sequences, expanded Easter eggs, financial-news feeds and automatic month-end cinematic summaries are not required by these stages. One small decorative milestone can remain if it is unobtrusive and conveys no extra financial claim. Month-level browsing and existing monthly stories remain in scope; a new narrative feature requires evidence of usefulness and explicit specification.

## Stage 1 — simplified revision implemented, awaiting user review

Use 15–21 September 2026 sample dates. The demo opens on 21 September. The compact road/date rail sits above a factual summary and recorded activity. At most one relevant illustration appears in the summary; everyday and quiet days do not need a landmark. There are no separate landmark menus or assistant panel. A compact semicircular mountain/sky vignette around the traveller reflects current device time, not the selected historical date. It uses illustrative hour bands without location/weather services. The traveller makes a brief position transition; text updates immediately. Quiet days have no invented insight. Money tools remain explicit placeholders. A quiet Tell us entry beneath the summary previews editable sample expenses before explicit confirmation, then updates in-memory activity for that date. It is not live AI extraction; additions disappear on reload and do not regenerate a story. Bottom navigation contains Journey and Money only.

Review: Can you immediately tell which date you are reading, what changed, and where to verify it? Does the illustration help without needing an explanation? Can you revisit a day and return from details without losing your place?

The static preview remains visibly sample-only. A private URL is not authentication; live personal data is gated by stage 04. Original frontend routes, components and deployment are unchanged.
