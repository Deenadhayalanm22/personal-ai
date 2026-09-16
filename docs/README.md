# Personal AI documentation

This directory is the source of truth for implemented product behavior and API contracts. Update the relevant module in the same change as an API or UI change.

## Feature contracts

- [Authentication and session](contracts/authentication.md)
- [Expenses and calendar](contracts/expenses.md)
- [Money stories](contracts/money-stories.md)
- [Recurring commitments](contracts/recurring-commitments.md)
- [Reference cleanup](contracts/reference-cleanup.md)
- [Loans and actions](contracts/loans-and-actions.md)
- [Mutual funds](contracts/mutual-funds.md)
- [Stocks](contracts/stocks.md)
- [Platform and WhatsApp](contracts/platform-and-whatsapp.md)

## Functional Jira specification

[`jira/`](jira/) is the cross-stack functional specification: each active epic owns the user journey, its backend behavior, its frontend behavior, and integration-test scenarios. It is deliberately outside `backend/` and `frontend/` because those epics span both.

## Documentation areas

`frontend/src/App.svelte` owns routing, session startup, online/offline refresh, and dashboard cache. `frontend/src/Home.svelte` renders the dashboard, stories, expense interactions, loans, actions, and mutual funds. `frontend/src/Normalization.svelte` owns reference cleanup; `Auth.svelte` owns sign-in. All browser calls go through `frontend/src/lib/api.js`.

- [`jira/`](jira/) — cross-stack functional specification and integration-test scenarios.
- [`contracts/`](contracts/) — current HTTP and integration contracts.
- [`engineering/`](engineering/) — backend and frontend local-development guides.
- [`testing/`](testing/) — test plans and regression notes.
- [`design/`](design/) — product architecture notes and visual design artifacts.
- [`experience/`](experience/) — client behavior such as offline caching.
- [`product/`](product/) — product requirements and vision documents.

The former `docs/backend/` and `docs/frontend/` folders have been retired. Their outdated API/prompt duplicates were removed; the current contracts and Jira epics replace them.
