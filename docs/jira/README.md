# Jira product documentation

This directory is the maintained product and delivery contract for Personal AI. It consolidates the earlier overview, conversational MVP, interpreter, cost, and live-model notes.

## Hierarchy

```text
INIT-001  Personal AI conversational finance assistant
├── EPIC-001  Conversational capture and orchestration
├── EPIC-002  Accounts, ledger integrity, and reconciliation
├── EPIC-003  Financial event coverage
├── EPIC-004  Queries, insights, and portfolio reporting
├── EPIC-005  Channels, identity, and reliable delivery
├── EPIC-006  AI quality, multilingual experience, and cost
└── EPIC-007  Production readiness, security, and operations
```

Jira calls the workstreams below the initiative “Epics.” Stories and their optional sub-tasks are kept in the epic file so requirements and delivery details do not fragment into dozens of tiny files.

## Files

| Key | Document | Purpose |
|---|---|---|
| INIT-001 | [Product initiative](INIT-001-personal-ai.md) | Vision, scope, success measures, release gates, and cross-epic rules |
| EPIC-001 | [Conversational capture](epics/EPIC-001-conversational-capture.md) | Interpretation, progressive capture, follow-ups, corrections |
| EPIC-002 | [Ledger integrity](epics/EPIC-002-ledger-integrity.md) | Accounts, balances, mutations, idempotency, reconciliation |
| EPIC-003 | [Financial events](epics/EPIC-003-financial-events.md) | Expenses, income, liability payments, transfers, and assets |
| EPIC-004 | [Insights](epics/EPIC-004-insights.md) | Expense queries, summaries, loans, assets, and portfolio reporting |
| EPIC-005 | [Channels and identity](epics/EPIC-005-channels-identity.md) | WhatsApp, REST, audio, users, sessions, and delivery |
| EPIC-006 | [AI quality and cost](epics/EPIC-006-ai-quality-cost.md) | Multilingual behavior, evaluations, deterministic responses, telemetry |
| EPIC-007 | [Production readiness](epics/EPIC-007-production-readiness.md) | Configuration, security, migrations, observability, and CI/CD |

## Ticket conventions

- **Status:** `Done` means implementation evidence exists in the repository; `In Progress` means the current code contains an incomplete seam; `To Do` is planned or not proven by code/tests. These are documentation assessments, not a substitute for Jira workflow state.
- **Acceptance criteria:** Use observable Given/When/Then behavior. A story is not Done until all criteria and required tests pass.
- **Sub-tasks:** Include engineering work only when it improves sequencing or ownership. Do not create a sub-task for every code edit.
- **Traceability:** Pull requests should name the ticket key. Update the story when scope, rules, or evidence changes.
- **New work:** Prefer adding a story to an existing epic. Add a new epic only when the work has a distinct outcome and several independently valuable stories.

## Definition of Ready

A story is ready when its user outcome, scope, dependencies, acceptance criteria, data/privacy effect, and test approach are understood. Unknowns that could change the solution are resolved or recorded as spikes.

## Definition of Done

A story is done when its acceptance criteria pass, relevant unit/integration tests exist, financial mutations remain idempotent and auditable, logs and metrics contain no sensitive/high-cardinality values, configuration is documented, and this backlog reflects the delivered behavior.
