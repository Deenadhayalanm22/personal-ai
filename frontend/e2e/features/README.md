# Browser feature scenarios

These `.feature` files are readable Gherkin specifications for the Playwright browser journeys in the parent `e2e/` folder.

- Add one feature file per user journey or major regression scenario.
- Keep its scenario steps aligned with the paired Playwright spec.
- The files are documentation only; Playwright executes the corresponding `.spec.js` test.

| Gherkin feature | Playwright test |
| --- | --- |
| `loan-commitment.feature` | `../loan-commitment.spec.js` |
| `mutual-fund-commitment.feature` | `../mutual-fund-commitment.spec.js` |
