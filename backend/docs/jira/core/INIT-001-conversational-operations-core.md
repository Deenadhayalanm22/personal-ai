# INIT-001 — Conversational Operations Core

| Field | Value |
|---|---|
| Issue type | Initiative |
| Status | In Progress |
| Outcome | Let people record and understand simple real-world operations through conversation instead of forms |
| Users | People with limited literacy, limited software familiarity, accessibility needs, or a preference for voice/chat |
| Channels | WhatsApp text and voice first; channel-neutral application API |

## Problem and promise

Most small operational software assumes that a user can navigate menus, understand field names, type accurately, and complete a form. The core removes that assumption. A user describes what happened; the system extracts known facts, asks only for information required by the installed extension, confirms consequential actions, stores an auditable event, and answers simple questions.

The core is not an ERP, accounting system, inventory system, or personal-expense system. It is the reusable conversational and ledger infrastructure on which those focused extensions run.

## Core domain model

| Primitive | Meaning | Examples supplied by extensions |
|---|---|---|
| Actor | Person or organization participating in an event | User, employee, supplier |
| Resource | Something whose quantity/value can be tracked | Rupees, thread, sarees, stock item |
| Unit | How a resource is measured | INR, metre, gram, piece |
| Event | A real-world occurrence described by the user | Expense, material issue, production surrender |
| Movement | Signed change to a resource/container caused by an event | -₹500 cash, -125 m thread, +20 sarees |
| Observation | A dated fact that does not itself imply a movement | Count, balance, weight, note |
| Rule | Extension-owned validation or calculation | Required fields, conversion standard, wage rate |

## Core principles

1. Conversation replaces forms; it does not reduce data integrity.
2. Save valid known facts before asking for optional enrichment.
3. Ask one short question at a time, in the user's language, with voice-friendly choices.
4. Language understanding may be probabilistic; validation, calculations, authorization, and persistence are deterministic.
5. Never invent a number, unit, actor, date, or business rule.
6. Every executed movement is attributable, auditable, and safe to retry.
7. Extensions are configuration/code packages using a versioned contract, not conditionals added to the core.

## Initiative acceptance criteria

1. **Given** two unrelated installed extensions, **when** each receives a supported message, **then** the same channel, conversation, extraction, event, movement, audit, and query infrastructure serves both without core code containing either domain's nouns.
2. **Given** incomplete but valid information, **when** the extension allows progressive capture, **then** known facts are retained and only the highest-value missing question is asked.
3. **Given** uncertain interpretation or missing required evidence, **when** execution is evaluated, **then** no consequential movement occurs until deterministic rules authorize it.
4. **Given** duplicate or concurrent delivery, **when** processed, **then** the event and its movements are applied at most once.
5. **Given** a user communicates by supported voice or text, **when** the task is completed, **then** they never have to switch to a form for the core workflow.

## Success measures

- Task completion rate and median turns, segmented by language, text/voice, and extension.
- Unsafe or incorrect consequential movements: zero target.
- Duplicate movements: zero target.
- Percentage of workflows completed without a form: 100% for declared core workflows.
- Clarification, abandonment, correction, latency, model-call, and cost rates.
- Usability validation with intended users, not only engineers or fluent smartphone users.

## Release boundary

The first release supports simple record, correct, query, and summarize workflows. It deliberately excludes generic workflow builders, arbitrary accounting, autonomous decisions/payments, complex forecasting, and a large ERP-style configuration UI.

## Epics

- [CORE-EPIC-001 — Inclusive channel and conversation experience](CORE-EPIC-001-inclusive-conversation.md)
- [CORE-EPIC-002 — Configurable extraction and extension runtime](CORE-EPIC-002-extension-runtime.md)
- [CORE-EPIC-003 — Generic event and movement ledger](CORE-EPIC-003-event-ledger.md)
- [CORE-EPIC-004 — Trust, quality, security, and operations](CORE-EPIC-004-trust-operations.md)
