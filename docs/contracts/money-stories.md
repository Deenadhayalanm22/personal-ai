# Monthly commitment presentation

`GET /api/web/monthly-commitment?month=YYYY-MM` returns `{ month, currency, timezone, commitment }` for the authenticated active profile. The month defaults to the current month in the profile timezone; historical months return `commitment: null` rather than showing today’s plan under an earlier date. `commitment` is the live `MONTHLY_COMMITMENT` presentation assembled from the canonical `monthly_financial_snapshot`; it is not a generated spending story or persisted publication. The endpoint never reads `money_story_snapshot`, `money_story`, or `money_story_evidence`; those tables are removed by migration V36.

The presentation retains `storyId: "monthly-commitment"`, `storyType: "MONTHLY_COMMITMENT"`, `period`, `cardFace`, ordered `cards`, and `evidence` for compatibility with the existing commitment reader. Only this type is returned. Current and next-month cards explain source buckets and planned totals. Evidence is scoped by card ID in `evidence.byCard`, and actions such as `OPEN_EVIDENCE` and `OPEN_SALARY_OUTLOOK` use the existing commitment and salary flows.

The data comes from current and next-month snapshot rows. Loan, SIP, recurring commitment, savings, stock-plan, and credit-card source updates rebuild those rows through their existing domain services. The presentation may include confirmed payment progress, due status, and optional salary context; it never derives account balances or treats a planned amount as paid. The chatbot reads owned financial facts independently of this presentation.

The Home page shows a single Monthly Commitment card. Generated spending-story cards, publication history, and the Stories menu are retired. Historical generated-story data is deleted with the three tables in V36.
