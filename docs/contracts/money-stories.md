# Money stories

Money stories are explainable cards in one ordered feed. Most are generated monthly observations about expense activity. The first card is always the live **Monthly commitment** planning story, derived on each read from active loans and mutual-fund SIPs; it is not a stored expense snapshot. The UI only renders server-supplied story data.

| Endpoint | Contract | Frontend owner |
| --- | --- | --- |
| `GET /api/web/expenses/monthly?month=YYYY-MM` | Optional month, defaulting to active profile's current month. Returns `{ month, currency, timezone, stories }`. | `App.svelte` loads/caches by profile and month. `Home.svelte` filters by source, opens card decks, and displays evidence. |

A story contains `storyId`, `storyType`, `templateVersion`, `generatedAt`, `period`, `cardFace`, `cards`, `evidence`, `level`, `logicalStoryId`, `revision`, `updatedReason`, and `observation`. `period` is `{ type, startDate, endDate, displayLabel }`; `cardFace` is `{ heading, displayValue, theme }`. Cards have ordered `sequence`, layout/theme, copy, components, and actions. The current UI recognizes `OPEN_EVIDENCE` and opens an evidence modal.

`MONTHLY_COMMITMENT` is always the first returned story. In v1 it totals only active loan EMIs whose tenure includes the current month and active mutual-fund SIPs that have started. It intentionally excludes stock holdings, income, balances, and recorded expenses. A future repeated-essential-expense component may be added only when its evidence rules are implemented.

Evidence has its title, count, total amount, and display-ready transaction rows (date, merchant, category, subcategory, amount). Expense edits and deletions request recalculation of affected stories. Treat IDs and revisions as server-owned; do not derive accounting totals from card copy.
