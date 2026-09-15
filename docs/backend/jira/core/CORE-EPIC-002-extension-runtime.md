# CORE-EPIC-002 — Configurable extraction and extension runtime

| Field | Value |
|---|---|
| Parent | INIT-001 |
| Status | To Do |
| Goal | Add a new domain without modifying core conversation or ledger logic |

## CORE-006 — Define a versioned extension manifest

**Status:** To Do · **Priority:** P0

An extension manifest defines its identifier/version, supported languages, actors, resource types/units, container types, event schemas, vocabulary/examples, required fields, follow-up policies, validators, movement planners, response templates, query types, permissions, and data migrations.

### Acceptance criteria

1. **Given** a valid manifest, **when** installed, **then** the runtime discovers its event types and capabilities without adding a switch statement to core orchestration.
2. **Given** an incompatible manifest/API version, **when** startup or installation occurs, **then** it fails with an actionable compatibility error.
3. **Given** two extensions with overlapping everyday words, **when** a message is interpreted, **then** tenant-enabled extensions and conversation context resolve or clarify the domain; both cannot execute accidentally.
4. Extension enable/disable and version are tenant-scoped and auditable.

### Sub-tasks

- CORE-006-A — Publish manifest JSON schema and Java service-provider interfaces.
- CORE-006-B — Build reference fixtures for personal expense and saree job work.
- CORE-006-C — Add compatibility and isolation tests.

## CORE-007 — Extract extension-defined events and units

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. The interpreter receives only enabled extension schemas and the minimum reference context required for the turn.
2. Extracted numeric values preserve value, unit, source evidence, and confidence; conversions are never performed by the LLM.
3. Synonyms and local speech supplied by the extension map to canonical fields without altering original text.
4. Unknown event types, fields, units, or references fail validation or trigger clarification.

## CORE-008 — Execute deterministic extension rules

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. An extension validates a proposed event and returns an explicit movement plan; only the core commits the plan.
2. Rule execution is deterministic, versioned, testable without an LLM, and cannot directly bypass tenant/actor authorization.
3. Calculation inputs, rule version, outputs, rounding, and rejection reason are audit-visible.
4. All planned movements commit atomically or none commit.

## CORE-009 — Provide extension-defined questions and reports

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. Extensions declare query intents, filters, deterministic aggregations, result schemas, and localized rendering templates.
2. The core supplies generic date/range/reference clarification and pagination behaviors.
3. Model-generated explanations cannot change calculated results.
4. Cross-extension reporting is disabled unless an explicit composed report owns its units and semantics.

## CORE-010 — Prove portability with a third reference extension

**Status:** To Do · **Priority:** P2 · **Depends on:** CORE-006 to CORE-009

### Acceptance criteria

1. A minimal grocery sales/stock extension is implemented using only the published contract.
2. It records a sale, stock receipt, and stock query without changing core code.
3. Portability findings update the contract before it is declared stable.
