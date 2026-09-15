# Personal AI documentation

This directory is the source of truth for implemented product behavior and API contracts. Update the relevant module in the same change as an API or UI change.

## Feature contracts

- [Authentication and session](contracts/authentication.md)
- [Expenses and calendar](contracts/expenses.md)
- [Money stories](contracts/money-stories.md)
- [Reference cleanup](contracts/reference-cleanup.md)
- [Loans and actions](contracts/loans-and-actions.md)
- [Mutual funds](contracts/mutual-funds.md)
- [Platform and WhatsApp](contracts/platform-and-whatsapp.md)

## Ownership

`frontend/src/App.svelte` owns routing, session startup, online/offline refresh, and dashboard cache. `frontend/src/Home.svelte` renders the dashboard, stories, expense interactions, loans, actions, and mutual funds. `frontend/src/Normalization.svelte` owns reference cleanup; `Auth.svelte` owns sign-in. All browser calls go through `frontend/src/lib/api.js`.

Legacy material remains grouped in `backend/`, `frontend/`, and `product/`.
