# Money stories

Money stories are explainable cards in one ordered feed. Most are generated monthly observations about expense activity. The first card is always the live **Monthly commitment** planning story, derived on each read from active loans and mutual-fund SIPs; it is not a stored expense snapshot. The UI only renders server-supplied story data.

| Endpoint | Contract | Frontend owner |
| --- | --- | --- |
| `GET /api/web/expenses/monthly?month=YYYY-MM` | Optional month, defaulting to active profile's current month. Returns `{ month, currency, timezone, stories }`. | `App.svelte` loads/caches by profile and month. `Home.svelte` filters by source, opens card decks, and displays evidence. |

A story contains `storyId`, `storyType`, `templateVersion`, `generatedAt`, `period`, `cardFace`, `cards`, `evidence`, `level`, `logicalStoryId`, `revision`, `updatedReason`, and `observation`. `period` is `{ type, startDate, endDate, displayLabel }`; `cardFace` is `{ heading, displayValue, theme }`. Cards have ordered `sequence`, layout/theme, copy, components, and actions. The current UI recognizes `OPEN_EVIDENCE` and opens an evidence modal.

`MONTHLY_COMMITMENT` is always the first returned story. Its current semantic buckets are `Debt repayments` (active loan EMIs whose tenure includes the current month) and `Planned investing` (active mutual-fund SIPs that have started). Its headline is the combined **full intended commitment**; individual loans and SIPs remain source evidence, with their monthly amount and due cadence. It intentionally excludes stock holdings, income, balances, and recorded expenses. Future buckets include essential living, emergency reserve, goal contributions, and protection commitments; a repeated-essential-expense component may be added only when its evidence rules are implemented.

Evidence has its title, count, total amount, and display-ready transaction rows (date, merchant, category, subcategory, amount). Expense edits and deletions request recalculation of affected stories. Treat IDs and revisions as server-owned; do not derive accounting totals from card copy.
