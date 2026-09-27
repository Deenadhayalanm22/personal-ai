# Money Stories — frontend v2

Independent Svelte/Vite application. Stage 1 uses local fixtures only: no backend, login, real records or financial mutations. Existing `frontend/` remains unchanged.

Use Node 20.19+ (a `.nvmrc` is included).

```sh
cd frontend-v2
npm ci
npm run dev
```

Open http://127.0.0.1:5174. `npm run build` outputs this app only to `frontend-v2/dist`; `npm run preview` serves that build on port 4174. Relative asset URLs support hosting the static demo in a subdirectory. This is not authenticated personal-data hosting; access enforcement is stage 04.

Browser verification: `npx playwright install chromium` once if needed, then `npm run test:e2e`. Tests start only v2 on port 5175 and never launch/reset the backend.

See [15-stage plan](../docs/design/v2/implementation-plan.md) and [active epic](../docs/jira/personal-expense/FIN-EPIC-007-journey-v2.md). Stage 1 includes date buttons, arrow/keyboard/touch travel, native date jump, one supporting SVG illustration per relevant day, evidence dialogs, reduced motion, and secondary-screen placeholders. Sample latest day is 21 September 2026, not real today. The 7-day fixture is deliberately bounded.

Assets are reusable inline SVG, with no external image/font requests or animation library. Later stages can replace individual symbols without replacing the scene renderer. Integration work must use server-owned facts and the documented existing contracts; no v1 components are imported or modified.

## Stage 1 verification

Production build passes. Sixteen Playwright checks pass across desktop and iPhone-sized Chromium: date navigation and limits, evidence dialogs, no backend calls/runtime errors, responsive overflow, secondary preview navigation, reduced motion, simulated touch travel, editable expense confirmation, cancellation, invalid-input handling, per-date additions and reload reset. Desktop and mobile screenshots were visually reviewed. Compressed initial HTML/CSS/JS total is approximately 27 KB; this is bundle size, not a measured load-time guarantee.

## Simplified stage 1 revision

The selected day now leads with a factual change summary, its amount and supporting details. A compact road/date strip replaces the panorama; at most one small tree, bridge or savings illustration supports the summary. Everyday/quiet days have no required illustration. Separate landmark menus and decorative headings are removed. A brief traveller position transition is disabled by reduced motion. There is no assistant panel or simulated AI response. Future assistance is scoped to selected-date/story/evidence context in stages 07 and 14.

## Tell us prototype

The day page accepts simple examples such as `Lunch ₹180 and Auto ₹90` with a clearly labelled local demo parser, not AI. Review and edit each proposed expense before adding it to that date's in-memory activity. Cancel makes no change; reload clears additions. Existing sample stories remain unchanged and are labelled accordingly; quiet days show a factual sample-activity summary after additions. Unsupported/ambiguous statements and questions are rejected without adding anything. Changing date resets an unsaved message. Bottom navigation is Journey / Money. Real extraction, date interpretation, type routing, persistence and WhatsApp integration remain stage 10 work.

## Ambient time detail

A compact semicircular sky around the traveller follows current device-local time: dawn 05–08, day 08–17, evening 17–20, night 20–05. It refreshes each minute and when tab visibility changes. It is illustrative, not a geographic sunrise/weather calculation, and is independent of the date being browsed. No network requests, location access or animation dependency are used.
