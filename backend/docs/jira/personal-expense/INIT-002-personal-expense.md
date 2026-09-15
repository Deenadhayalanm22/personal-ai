# INIT-002 — Personal Expense Extension

| Field | Value |
|---|---|
| Issue type | Initiative |
| Status | In Progress |
| Depends on | INIT-001 extension contract and event ledger |
| Outcome | Help an individual record income/spending and understand personal cash flow conversationally |

## Boundary

This extension owns expenses, income, personal accounts, categories, merchants, transfers, liabilities, assets, INR defaults, financial validation, and reports. WhatsApp, voice, generic follow-ups, events, movements, idempotency, audit, localization mechanics, and security belong to INIT-001.

## Initiative acceptance criteria

1. A user can set up an account and record/query common income and expense activity without a form.
2. A financial amount changes state only from current-message or confirmed pending evidence and deterministic finance rules.
3. Unknown balances remain unknown; activity insights work before reconciliation while exact balance claims do not.
4. Retried expenses, income, payments, or transfers never duplicate financial effects.
5. The extension can be disabled without disabling the core or another tenant's extension.

## Epics

- [FIN-EPIC-001 — Personal accounts and financial capture](FIN-EPIC-001-capture.md)
- [FIN-EPIC-002 — Financial correctness and reconciliation](FIN-EPIC-002-correctness.md)
- [FIN-EPIC-003 — Personal spending insights](FIN-EPIC-003-insights.md)
