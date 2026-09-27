# FIN-EPIC-007 — Isolated Money Stories journey v2

Status: Stage 1 simplified revision implemented; verification recorded in frontend-v2/README.md; later stages planned, not shipped.

## Scope and isolation

`frontend-v2/` is a separately built and hosted Svelte application alongside `frontend/`. Existing production UI and backend behavior remain unchanged. Stage 1 is a local, sample-data concept with no API reads or writes, no authentication, and no real financial data. Hosting a later version with personal data requires server-enforced owner-only access; a separate URL alone is insufficient.

## Stage 1 acceptance criteria

1. The app prominently identifies sample data and stage 1. Opening it sends no backend requests and performs no financial mutations.
2. Previous/next, date buttons, keyboard arrows on the date buttons, and horizontal touch gestures select the same sample day, bounded by the fixture range. Native date selection supports jumping to a sample date; a reset control returns to the demo's latest date, not the real wall-clock today.
3. The selected date leads to a factual primary change summary and readable activity. At most one supporting visual appears: tree for recorded contributions, bridge for repayment milestones, shelter for recorded savings. Everyday and quiet days have no required landmark. No city panorama or separate landmark controls compete with the summary. Quiet days say no activity recorded and do not offer a fabricated story.
4. Evidence details explain the selected sample event in an accessible dismissible dialog and preserve the selected date on return. A compact road supports date travel; the traveller can move briefly but content updates immediately. Reduced-motion preference disables nonessential animation.
5. Small and large screens remain usable without page overflow. Production build and browser tests pass independently of the existing frontend and backend.
6. Journey/Money navigation is scoped to v2. Money tools remain explicit previews. A quiet Tell us entry sits beneath the day summary, with Journey and Money as the only bottom navigation actions.

7. The primary summary and its key value fit within the initial 390 × 844 phone viewport; illustrations are decorative and their information is in text. No assistant panel or generated advice is presented in stage 1.

8. Tell us accepts deliberately limited sample label/amount messages and opens editable expense previews for the selected date. No AI service is called. Confirmation explicitly adds sample rows only to in-memory date-scoped activity; cancel changes nothing, reload clears them, and an unchanged story is labelled after additions. An empty day with new sample rows shows a factual activity summary rather than an invented AI story.
9. Empty/partial/invalid messages and questions do not add records. Unsupported payment types, date wording and conversational statements receive an explanatory message. The preview validates positive amounts to two decimal places and nonblank descriptions. Changing date resets the unsaved message; drafts cannot silently move between dates. Future live extraction and saving remain stage 10 work.

10. A compact semicircular sky behind and above the traveller follows the current device-local hour, independently of the selected story date: dawn 05:00–07:59, day 08:00–16:59, evening 17:00–19:59, night 20:00–04:59. Night shows a moon and stars. Refresh on mount, each minute and tab visibility changes; clean up timer/listener on unmount. These are illustrative clock periods, not geographic sunrise/sunset or weather. No location request, service call, financial meaning or recurring animation is introduced. Reduced motion removes the brief sun-position transition.

## Product decision rules

The core is understanding what happened, what changed and what needs attention. Navigation, factual summaries and evidence take priority over animation or scenery. A quiet day is not a financial success claim. New visual mechanics require a demonstrated comprehension benefit. Rich city-building, additional Easter eggs and cinematic summaries are deferred. The approved tiny clock-based sky is an ambient exception kept within the existing road strip, following the traveller without adding city elements or increasing the strip height.

## Proposed later behavior

Daily history must distinguish effective/payment date from recording date. Published stories and their evidence retain their original version; later corrections are explicitly labelled. Supporting visual state must be reproducible from verified facts and versioned rules, with no inference of balance, returns, payment completion, or zero spending from missing data. Trees represent contribution milestones, not gains; completed bridges require verified closure. Before live implementation, update this epic and affected contracts with the precise agreed semantics and acceptance tests.

## Delivery plan

See [15-stage plan](../../design/v2/implementation-plan.md). Each stage is a separate review checkpoint. No automatic daily schedule or automatic progression is implied.

## Future assistant context

Stage 07 establishes stable context references; stage 14 specifies a permission-checked selected-date/story-version/evidence context contract and a read-only prototype. No live assistant or financial writes are introduced by this revision. Future mutation actions require explicit confirmation and separately documented contracts. Backend ownership checks must revalidate every context reference.
