# Jira product documentation

This directory is the maintained delivery contract for the code in this repository. It is not a roadmap for a generic conversational platform. The Personal Expense initiative is the source of truth for delivered behavior; its stories are written as executable integration-test scenarios.

## Hierarchy

```text
INIT-002  Personal Expense Extension
├── FIN-EPIC-001  Conversational expense capture
├── FIN-EPIC-002  Expense data correctness and repair
├── FIN-EPIC-003  Personal spending calendar and insights
├── FIN-EPIC-004  Portal access and reference hygiene
└── FIN-EPIC-005  Loans and mutual-fund planning
```

The codebase has no extension manifest/runtime, generic event-and-movement ledger, tenant platform, or Saree Job-Work extension. Earlier core documents are historical proposals only, not acceptance criteria for this final code.

## Files

| Key | Document | Purpose |
|---|---|---|
| INIT-002 | [Personal Expense Extension](personal-expense/INIT-002-personal-expense.md) | Implemented expense, portal, planning, and insight behavior |

## Ticket conventions

- **Status:** `Done` means implementation evidence exists in the repository; `In Progress` means a current code seam remains incomplete; `Not implemented` means intentionally absent from this final codebase.
- **Acceptance criteria:** Use observable Given/When/Then behavior. A story is not Done until all criteria and required tests pass.
- **Sub-tasks:** Include engineering work only when it improves sequencing or ownership. Do not create a sub-task for every code edit.
- **Traceability:** Pull requests should name the ticket key. Update the story when scope, rules, or evidence changes.
- **New work:** Add a story to the appropriate personal-expense epic. Do not imply a generic extension framework unless it is implemented and tested.

## Definition of Ready

A story is ready when its user outcome, scope, dependencies, acceptance criteria, data/privacy effect, and test approach are understood. Unknowns that could change the solution are resolved or recorded as spikes.

## Definition of Done

A story is done when its acceptance criteria pass, relevant tests exist, movements remain idempotent and auditable, logs and metrics contain no sensitive/high-cardinality values, accessibility has been checked with intended users, and this backlog reflects delivered behavior.

## Historical proposals

`core/` contains pre-finalization proposals. It must not be used to derive tests, customer commitments, or feature claims. Use `personal-expense/` and [`docs/contracts`](../../../README.md) for current behavior.
