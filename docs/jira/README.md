# Jira product documentation

The implementation approach for separating the reusable platform from business modules is documented in the [core and business-extension segregation plan](../architecture/core-extension-segregation-plan.md). The normative rules for choosing between the generic ledger, extension-owned tables, and legacy finance tables are in [extension persistence rules](../architecture/extension-persistence-rules.md).

This directory is the maintained product and delivery contract for a reusable conversational operations platform and its domain extensions. The core exists for people who cannot or do not want to complete software forms; WhatsApp text or voice becomes the interface for simple record keeping and insights.

## Hierarchy

```text
INIT-001  Conversational Operations Core
├── CORE-EPIC-001  Inclusive channel and conversation experience
├── CORE-EPIC-002  Configurable extraction and extension runtime
├── CORE-EPIC-003  Generic event and movement ledger
└── CORE-EPIC-004  Trust, quality, security, and operations

INIT-002  Personal Expense Extension
├── FIN-EPIC-001  Personal accounts and financial capture
├── FIN-EPIC-002  Financial correctness and reconciliation
└── FIN-EPIC-003  Personal spending insights

INIT-003  Saree Job-Work Extension
├── SAREE-EPIC-001  People, materials, and configurable standards
├── SAREE-EPIC-002  Material issue, production, and surrender
├── SAREE-EPIC-003  Weekly wages and settlement
└── SAREE-EPIC-004  Operational insights and exception control
```

The three initiatives have independent outcomes and release plans. INIT-002 and INIT-003 depend on INIT-001 through the extension contract; they must not add personal-finance or saree-specific behavior to the core.

## Files

| Key | Document | Purpose |
|---|---|---|
| INIT-001 | [Conversational Operations Core](core/INIT-001-conversational-operations-core.md) | Reusable product boundary and extension contract |
| INIT-002 | [Personal Expense Extension](personal-expense/INIT-002-personal-expense.md) | Personal income, expense, accounts, and insights |
| INIT-003 | [Saree Job-Work Extension](saree-job-work/INIT-003-saree-job-work.md) | Thread issue, saree surrender, wages, inventory, and insights |

## Ticket conventions

- **Status:** `Done` means implementation evidence exists in the repository; `In Progress` means the current code contains an incomplete seam; `To Do` is planned or not proven by code/tests. These are documentation assessments, not a substitute for Jira workflow state.
- **Acceptance criteria:** Use observable Given/When/Then behavior. A story is not Done until all criteria and required tests pass.
- **Sub-tasks:** Include engineering work only when it improves sequencing or ownership. Do not create a sub-task for every code edit.
- **Traceability:** Pull requests should name the ticket key. Update the story when scope, rules, or evidence changes.
- **Extension rule:** Domain nouns, prompts, calculations, and reports belong to the relevant extension. Only behavior useful to at least two materially different domains is a candidate for the core.
- **New work:** Prefer adding a story to an existing epic. Add a new initiative only for an independently valuable domain extension.

## Definition of Ready

A story is ready when its user outcome, scope, dependencies, acceptance criteria, data/privacy effect, and test approach are understood. Unknowns that could change the solution are resolved or recorded as spikes.

## Definition of Done

A story is done when its acceptance criteria pass, relevant tests exist, movements remain idempotent and auditable, logs and metrics contain no sensitive/high-cardinality values, accessibility has been checked with intended users, and this backlog reflects delivered behavior.

## Product boundary test

Before adding a core capability, ask: “Would both a personal expense user and a saree job-work owner need this unchanged?” If not, keep it in an extension. The core may know `resource`, `unit`, `event`, `movement`, `actor`, and `question`; it must not know `expense`, `bank account`, `thread`, `saree`, or `wage`.
