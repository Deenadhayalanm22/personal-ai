# FIN-EPIC-007 — Isolated Money Stories journey v2

Status: Stages 1–2 implemented as isolated samples; stages 3–15 planned. See [15-stage plan](../../design/v2/implementation-plan.md).

## Isolation
Independent Svelte app in frontend-v2; existing frontend and backend unchanged. Stage 1 makes no API calls and contains no authentication or real financial records. All additions and decisions reset on reload. Live personal-data hosting requires server-enforced owner-only access, not merely a separate URL.

## Stage 1 acceptance criteria
1. Clearly identify sample data and fixed demo today, 21 September 2026. No backend requests or financial mutations.
2. Monthly context sits above chronology. Recorded expenses, planned commitments, investments and money set aside remain separately labelled; no implied bank balance or invented net projection. Breakdown explains overlapping source totals and the bounded sample scope.
3. Newest-first vertical date groups cover 15–22 September. Today starts expanded; tomorrow is a plan. Multiple dates can expand independently using keyboard or pointer. Today and native date jump open/focus the correct group. Invalid jumps explain the fixture bounds.
4. Overdue items remain discoverable through a link to their original dated occurrence. No duplicate actionable card is created. Future plans have no record/skip controls.
5. Eligible sample occurrences offer inline Record and Skip confirmation, with one active action form. Cancelling changes nothing. Commitment recording accepts actual amount/date; loan recording fixes planned amount and demo-today date; optional skip penalty must be positive if supplied. Investment requires positive amount and units and derives NAV. Savings explicitly records money set aside, not an insurance payment.
6. Confirmed outcomes remove actionable controls and reject repeated decisions. The source occurrence shows its result; recording-day activity references the same event rather than counting it as another expense. Loan skip describes a one-month extension but does not implement a real schedule engine.
7. Monthly sample planned total retains recorded ordinary commitments, excludes skipped occurrences and excludes completed savings contributions. Investing and savings totals update independently. Recording these outcomes does not add standalone expenses. These fixture rules do not replace production source contracts.
8. Existing story summaries, restrained illustrations and dismissible evidence remain accessible. Quiet days say no activity recorded. Additions do not silently rewrite published sample stories; the unchanged story is labelled. Missing activity is never interpreted as zero spending or success.
9. One Tell us composer defaults to demo today independently of scrolling. Past-date capture requires an explicit dated action. Changing capture date resets its unsaved message. A limited local label/amount parser opens editable previews, then adds rows only on confirmation. Questions, unsupported types, partial/invalid messages, blank descriptions and nonpositive/overprecision amounts are rejected. No AI is called.
10. Journey/Money navigation preserves expanded groups and returns to the journey position. Money management and profile remain clearly labelled previews. Full forms/history/corrections will stay reachable outside chronology.
11. One small semicircular mountain/sky around a traveller follows device-local time, independently of the historic/demo date: dawn 05–08, day 08–17, evening 17–20, night 20–05. Refresh on mount/minute/visibility, clean up listeners, honor reduced motion. No weather/location lookup or city is introduced.
12. Desktop and phone layouts avoid horizontal overflow. Build and browser checks cover navigation, financial state changes, cancellation, validation, capture scope, evidence, clock phases, keyboard access, reset and absence of backend calls.

## Stage 2 acceptance criteria
1. Closed date groups expose the day’s key recorded amount and type, or the amount/status of an overdue or upcoming item. The user can compare busy and quiet days without opening every card. Planned amounts remain labelled as planned.
2. An open group places a short story preview and its evidence link before routine scheduled items. The full sample explanation remains available in the details dialog. The preview limits visible prose; it does not discard evidence. New sample actions label the original published story as unchanged while activity and monthly facts update.
3. Today’s due items, 19 September’s overdue loan, 17 September’s multiple expenses and the quiet day remain distinct. The overdue link continues to reach one original actionable occurrence. After a skipped or recorded decision, the collapsed summary no longer suggests the item is still due.
4. A switchable first-use preview contains no fabricated ₹0 month, obligations, past stories, or historic date groups. It invites one confirmed sample expense, then shows the recorded item and amount with no generated story. The populated sample journey remains available for comparison. First-use records are temporary and isolated from the populated fixture.
5. Decorative SVGs remain small and optional; no information depends on them. Keyboard actions, mobile layout and focusable evidence links remain usable. No new external assets or network calls are introduced.
6. Browser checks cover these scenarios on desktop and phone widths, including first entry, narrative ordering, absence of fictitious totals, summary updates and overflow.

## Future contracts and ownership
Live inline actions must reuse the existing domain commands and validations used by Money. A reusable occurrence identity, eligibility/read model and source-specific forms are required before integration. Loan, commitment, savings, funds and stocks retain their distinct semantics. Early payments, extra payments, restructuring, closure, corrections and destructive actions remain domain-owned; not every operation belongs on a date card.

Daily summaries need an owned combined read composition; expense-calendar totals alone cannot supply investment/savings facts. Fetch bounded batches and cache by profile/period; expansion must not imply one database request per scroll. Historic story snapshots and live monthly commitments have separate time scopes. Never label current commitments as an earlier month's projection.

Published narratives/evidence require explicit version/history contracts before live reconstruction. Assistant context includes owner, date/coverage period, story version, publication time and resolvable evidence. A dedicated conversation view will handle questions across dates; no fabricated answer or automatic financial write is introduced here. Owning epics/contracts and acceptance tests must be updated when these integrations ship.

## Product rules
The priority is understanding money and taking relevant action with minimal clutter. Ambient art cannot imply financial health. Trees represent contributions, not returns; bridge closure requires verified closure. Expanded scenery, news feeds and city-building are deferred.
