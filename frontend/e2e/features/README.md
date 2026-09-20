# Browser feature scenarios

These `.feature` files are readable Gherkin specifications for the Playwright browser journeys in the parent `e2e/` folder.

- Add one feature file per user journey or major regression scenario.
- Keep its scenario steps aligned with the paired Playwright spec.
- The files are documentation only; Playwright executes the corresponding `.spec.js` test.

| Gherkin feature | Playwright test | Product documentation |
| --- | --- | --- |
| `loan-commitment.feature` | `../loan-commitment.spec.js` | [`FIN-EPIC-005` planning](../../../docs/jira/personal-expense/FIN-EPIC-005-planning.md) |
| `mutual-fund-commitment.feature` | `../mutual-fund-commitment.spec.js` | [`FIN-EPIC-005` planning](../../../docs/jira/personal-expense/FIN-EPIC-005-planning.md) · [`mutual-funds` contract](../../../docs/contracts/mutual-funds.md) |
| `etf-monthly-plan.feature` | `../stocks.spec.js` | [`FIN-EPIC-005` planning](../../../docs/jira/personal-expense/FIN-EPIC-005-planning.md) · [`stocks` contract](../../../docs/contracts/stocks.md) |
| `recurring-commitments.feature` | `../recurring-commitments.spec.js` | [`FIN-EPIC-006` recurring commitments](../../../docs/jira/personal-expense/FIN-EPIC-006-essential-commitments.md) · [`recurring commitments` contract](../../../docs/contracts/recurring-commitments.md) |
| `stocks.feature` | `../stocks.spec.js` | [`FIN-EPIC-005` planning](../../../docs/jira/personal-expense/FIN-EPIC-005-planning.md) · [`stocks` contract](../../../docs/contracts/stocks.md) |
