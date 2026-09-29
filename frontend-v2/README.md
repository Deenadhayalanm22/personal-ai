# Money Stories — frontend v2

Independent Svelte/Vite application. The default local build preserves the stages 1–3 sample. A separate live build uses existing authenticated backend APIs through a private v2 host. The shared backend remains unchanged. The v1 Money component has an optional embedded entry point for v2; its default behavior remains unchanged.

Use Node 20.19+:

```sh
cd frontend-v2
npm ci
npm run dev
```

Open http://127.0.0.1:5174. Build with npm run build; output is frontend-v2/dist. npm run preview serves it on port 4174. Relative assets support a subdirectory. This build is for local design review only; private hosting remains a future stage.

## Vertical stage 1
- Monthly context separates recorded expenses and planned commitments; View month opens investing, savings and source details.
- Expandable dates cover 15–22 September 2026; demo today is 21 September. Date jump and Today preserve independent expanded groups.
- Tomorrow shows an upcoming plan. An overdue link jumps to its original due-date card.
- Inline sample Record/Skip confirmations cover commitment, loan, investment and savings; each collects different required facts. Confirmation updates the card/activity/monthly measures. No real payment or schedule mutation occurs.
- One Tell us entry defaults to demo today. Explicit dated actions target older days. A local parser previews editable label/amount expenses before confirmation; it is not AI.
- Stories remain unchanged after additions, with a label. Reload clears all changes.
- Journey/Money navigation preserves history context. Management destinations remain previews.
- One small semicircular SVG sky follows device time; no city, external assets or animation library.

Sample totals are bounded fixtures, not a complete monthly forecast or account balance. Loan skip explains the extension but does not compute a new schedule. Production financial facts and commands must come from their owning backend contracts.

## Stage 2 review
Closed date cards show the important amount and its meaning. The short story and evidence action appear first when a date expands; routine action cards follow. New sample actions label the original story as unchanged. The footer’s “Preview first-use state” switch shows an empty month without zero-value claims or invented stories. Confirm a sample expense there to inspect the first recorded day, then switch back to the populated journey. These preview records reset on reload.

## Verification
npm run test:e2e runs desktop and phone-sized Chromium checks on v2 port 5175, without starting the backend. Install Chromium once with npx playwright install chromium if needed. Checks cover monthly context, expandable history, upcoming/overdue distinction, inline decisions, cancellation/validation, capture date/confirmation, reload reset, evidence, keyboard navigation, overflow and clock phases.

See the [15-stage plan](../docs/design/v2/implementation-plan.md) and [active epic](../docs/jira/personal-expense/FIN-EPIC-007-journey-v2.md). The [horizontal reference archive](../docs/design/v2/references/stage01-horizontal.zip) preserves the prior prototype.

Stage 1 verification: production build and 16 desktop/mobile browser checks passed. Stage 2 production build and 20 desktop/mobile browser checks passed; screenshots were visually reviewed. Initial compressed HTML/CSS/JS is approximately 34.2 KB, excluding transport overhead; this is a bundle measurement, not a load-time guarantee.

## Stage 3 navigation

Month and date controls cover August–October 2026. September is the only populated sample month; other dates explicitly say data is unavailable. Date, story, task and activity detail links survive reload and browser Back. Busy dates preview three activities with Show all; 13–14 September form an expandable quiet stretch.

## Stage 4 private deployment

The private-host implementation and local access tests are present. Deployment remains pending until the separate v2 host and magic-link origin are configured and verified.

## Frontend-only live mode, stages 4–15

The original sample remains the default local build. `npm run build:live` builds the separate live UI. `V2_API_ORIGIN` and `V2_PUBLIC_ORIGIN` are required when starting the private host with `npm run start:private`; the v2 origin must match the existing backend magic-link base URL for sign-in links to return to this host. The user will provide the deployment host later. The private host serves the existing sign-in component publicly, gates the Journey app HTML and assets on the existing `WEB_SESSION`, proxies existing `/api/web` APIs, and fails closed if the backend session check is unavailable. No backend source was changed.

The live UI reads the existing expense calendar, monthly stories, source lists, and cursor-paged expenses. It shows current-month commitments only within their valid period; older month projections and earlier story publications are labelled unavailable. Dated source items open the matching loan, commitment, fund, or stock in the existing Money component. That component supplies the proven payment, skip, history, correction, and management forms through its existing endpoints. The Journey does not submit a separate payment outcome. Expense corrections/deletions use the existing owned expense APIs. Capture uses the existing WhatsApp date handoff. Explore is a read-only published-story context viewer. The existing backend does not offer a combined daily summary, historical story publication read, or direct web conversational capture, so those stage gates remain open.

Run `npm run test:unit` for adapter, navigation, and private-host tests, and `npm run test:live` for mocked desktop/mobile browser checks. See the [active epic](../docs/jira/personal-expense/FIN-EPIC-007-journey-v2.md) for accepted scope and remaining stage gates.
