# INIT-002 — Personal Expense Extension

| Field | Value |
|---|---|
| Issue type | Initiative |
| Status | In Progress |
| Depends on | INIT-001 extension contract and event ledger |
| Outcome | Help an individual capture expenses conversationally, review spending, and maintain selected planning records |

## Boundary

This extension currently owns conversational expense capture, expense categories, merchants and source accounts, calendar and story views, magic-link portal access, loans, mutual funds, and user-specific demo profiles. WhatsApp delivery, generic voice/text extraction mechanics, security primitives, and infrastructure belong to INIT-001. Income, transfers, reconciled balances, and generic ledger movements are not implemented product behavior and must not be described as available.

## Implemented user journey

1. The user sends an expense or confirmation through WhatsApp; the backend stores a draft, normalizes it, and replies.
2. The user requests a one-time portal link through WhatsApp and exchanges it for a cookie-backed web session.
3. The dashboard presents a selected month's calendar, recent expenses, and generated stories. The user can correct or soft-delete an expense.
4. The user can standardize reference names, add planning data (loans or mutual funds), and confirm due SIPs.

Each epic contains the behavior, state changes, and integration-test scenarios for one part of that journey. The endpoint-level schema remains in the [contract index](../../../README.md).

## Initiative acceptance criteria

1. A user can request a one-time portal link, capture an expense through WhatsApp, and review the resulting transactions without creating an income, transfer, or balance record.
2. An expense changes state only from confirmed conversational evidence or an authenticated, owned portal edit with deterministic validation.
3. Spending views and stories explain recorded expenses; they do not claim account balances, income, transfer, or reconciliation data.
4. Retried captured expenses and SIP confirmations do not duplicate the underlying financial transaction.
5. Every portal read and write is resolved to the authenticated user's active real or demo profile.

## Epics

- [FIN-EPIC-001 — Conversational expense capture](FIN-EPIC-001-capture.md)
- [FIN-EPIC-002 — Expense data correctness and repair](FIN-EPIC-002-correctness.md)
- [FIN-EPIC-003 — Personal spending calendar and insights](FIN-EPIC-003-insights.md)
- [FIN-EPIC-004 — Portal access and reference hygiene](FIN-EPIC-004-portal-and-references.md)
- [FIN-EPIC-005 — Loans and mutual-fund planning](FIN-EPIC-005-planning.md)
