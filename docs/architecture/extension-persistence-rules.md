# Extension persistence rules

## Status and intent

This document is the persistence decision for the conversational operations platform. It is intended
for maintainers and coding agents adding or evolving business extensions.

The platform remains a modular monolith: one Spring Boot deployment and one PostgreSQL database are
the default. A new extension does **not** require a separate service, database, or infrastructure stack.
Logical ownership is enforced through modules, table prefixes or schemas, migrations, and dependency
rules.

## The three persistence layers

### 1. Generic immutable ledger owned by core

All consequential extension activity is committed through the generic core ledger:

| Table | Meaning |
|---|---|
| `core_event` | Immutable occurrence, normalized facts, evidence, actor, time, schema/rule version, causation, and idempotency identity |
| `core_movement` | Signed quantitative effect on a resource/container in an explicit unit |
| `core_observation` | Dated measurement or fact that does not itself imply a movement |

Examples include 1,000 metres assigned, 24 pieces surrendered, ₹2,400 earned, five kilograms received,
or a dated stock count. Core understands only neutral concepts such as event, resource, container,
quantity, unit, actor, and observation. It must not gain saree, grocery, hardware, tea-shop, account, or
expense columns.

The generic ledger is the audit source of truth. It provides:

- append-only history;
- tenant-scoped idempotency;
- evidence and traceability;
- compatible-unit validation;
- balanced, atomic movements;
- replay input for rebuilding extension projections.

An extension must submit an `ExecutionPlan` through the core ledger service. It must not bypass the
ledger for a consequential operation merely because it also maintains a projection.

### 2. Extension-owned relational projections

Queryable current state belongs to the owning extension. As a domain grows, add strongly typed tables
inside that extension rather than adding domain fields to core or repeatedly scanning `facts` JSON.

Examples:

```text
saree_batch
saree_surrender
saree_wage_statement
saree_payment

fin_account
fin_expense_projection

grocery_product
grocery_stock_balance
grocery_sale
grocery_supplier
```

These tables are read models, not competing event ledgers. Each projection must:

1. be owned and migrated by its extension;
2. reference the source `core_event_id` or otherwise retain an unambiguous event link;
3. have an idempotency/uniqueness constraint that prevents applying one event twice;
4. be updated in the same transaction as the committed outcome when supported by the extension contract;
5. be reconcilable with, and preferably rebuildable from, the immutable ledger;
6. keep tenant identity in every queryable aggregate;
7. use explicit units and never combine incompatible quantities;
8. avoid storing secrets or unnecessary raw conversation content.

Use an extension prefix such as `saree_*`, `fin_*`, or `grocery_*`, or an equivalent PostgreSQL schema.
Keep Flyway migrations packaged with the owning module.

Reading `core_event.facts` directly is acceptable for an initial vertical slice. It is not the intended
long-term query model for workflows involving multiple aggregates, partial settlement, reporting,
concurrency, or large histories.

### 3. Legacy finance compatibility tables

The following tables predate the generic ledger and belong to the personal-finance extension:

| Table | Legacy finance responsibility |
|---|---|
| `state_container` | Mutable account, cash, credit-card, or other financial-container snapshot |
| `state_change` | Expense, income, transfer, or other finance transaction |
| `state_mutation` | Debit/credit adjustment produced by a finance transaction |

Their names appear generic, but their columns and semantics are finance-specific: currency, amount,
credit limits, `financially_applied`, debit, credit, and payment behavior. They are not platform APIs.

New extensions, including saree, grocery, hardware, and tea-shop extensions, must not depend on or write
to these tables. Finance may temporarily dual-write or project into them while legacy reads are migrated.
Remove them only after finance reads have moved to `core_event` plus `fin_*` projections and reconciliation
proves equivalent behavior.

## Decision guide for a new field or table

Before changing persistence, apply these tests in order:

1. **Is it an immutable real-world occurrence or quantitative effect?** Put the occurrence in
   `core_event` and the signed effect in `core_movement`; use `core_observation` for a measurement.
2. **Is it current/queryable state for one business domain?** Add an extension-owned projection.
3. **Can two materially unrelated extensions explain the proposed core field without using either
   domain's vocabulary?** If not, it does not belong in core.
4. **Is it only needed by finance compatibility code?** Keep it under `modules/expense` and do not expose
   it as an extension contract.
5. **Does a second extension need genuinely identical behavior?** Prefer a small reusable capability
   extension, such as payments, over copying tables or placing domain logic in core.

Similarity of names is not sufficient evidence for a shared abstraction. A saree batch, grocery stock
lot, and credit-card account can all be described as a “container,” but their lifecycle, constraints, and
query state remain extension-owned.

## Saree evolution example

The first saree MVP may resolve `SW-101` and wage context from immutable event facts. When the workflow
supports multiple open batches, partial surrenders, partial payments, closure, corrections, or reporting,
introduce projections such as:

```text
saree_batch
  tenant_id, batch_code, employee_id, assigned_quantity, assigned_unit,
  status, opened_at, closed_at, source_event_id

saree_surrender
  tenant_id, batch_id, surrendered_pieces, accepted_pieces,
  occurred_at, source_event_id

saree_wage_statement
  tenant_id, batch_id, employee_id, earned_amount, approved_amount,
  paid_amount, outstanding_amount, status, source_event_id

saree_payment
  tenant_id, wage_statement_id, amount, payment_method,
  occurred_at, source_event_id
```

The exact schema may evolve, but the invariant does not: core retains immutable generic truth; the saree
extension owns efficient saree state.

## Infrastructure rule

For the expected early product scale, use one application deployment and one PostgreSQL cluster/database
with tenant isolation and extension-scoped ownership. Do not create one deployment or database per
extension or customer unless security, regulatory isolation, measured scaling, or contractual requirements
justify it. Modular boundaries should make a later split possible without paying the operational cost now.

## Rules for coding agents

- Do not use `state_container`, `state_change`, or `state_mutation` in a new extension.
- Do not add domain nouns or extension-specific columns to core ledger tables.
- Do not treat JSONB as the permanent read model for a growing domain.
- Do not create a second source of truth in an extension projection.
- Do preserve event identity, causation, evidence, rule/schema versions, and units.
- Do add extension migrations and projection reconciliation tests when queryable state is introduced.
- Do update this decision if the platform intentionally changes its persistence boundary.

