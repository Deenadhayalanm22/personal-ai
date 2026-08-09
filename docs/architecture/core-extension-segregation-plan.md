# Core and business-extension segregation plan

## Decision

Evolve the application into a **modular monolith with a versioned extension contract**. Keep one deployable Spring Boot application and one PostgreSQL database initially, but split the build into modules whose dependency direction is enforced at compile time and by architecture tests.

Do not model a business type by subclassing a core `Business` class. A business type is a tenant-scoped **composition of enabled capabilities**. For example, a saree job-work business may enable `saree-job-work` and the reusable `payments` capability; a personal user may enable only `personal-finance`. This permits another business to reuse payments without inheriting saree or expense behavior.

The governing dependency rule is:

```text
business extensions --> extension API --> core
                                      --> shared test kit

bootstrap/application --> core + selected extensions + adapters

core -X-> personal-finance
core -X-> saree-job-work
extension A -X-> extension B (unless an explicit integration module owns the relationship)
```

`-X->` means the dependency is forbidden.

## Baseline coupling that motivated the migration

The following findings describe the pre-segregation baseline. The Maven split, generic extension runtime,
module-owned persistence/resources, and architecture rules now address these structural problems; remaining
`finance.legacy` packages are an explicit incremental internal cleanup within the expense module:

1. `UnifiedConversationEngine` injects `ExpenseHandler`, imports `HumanAmountParser`, branches on `EXPENSE`, understands finance follow-up fields, and builds account context.
2. `OpenAiConversationInterpreter` has a fixed finance intent list, fixed finance fields, bookkeeping instructions, and account/payment rules in its core prompt.
3. `EventFields` is a closed financial DTO. Adding material issue, production, wages, or another business requires modifying core conversation classes.
4. `ConversationMessages` renders `ExpenseSummary`, so even user-facing core conversation depends on a finance result type.
5. `StateChangeRepository` contains expense/account-specific aggregations and depends on a DTO outside core.
6. The generic-looking persistence model still uses finance terms such as `transaction`, `amount`, `financiallyApplied`, `DEBIT`, and `CREDIT`. It cannot faithfully express metres, pieces, grams, observations, and balanced multi-leg movements.
7. Spring component scanning makes every handler globally available. There is no tenant-aware extension enablement, version compatibility check, or namespace isolation.
8. All packages compile in one Maven artifact, so package naming is convention rather than an enforceable boundary.

Package movement alone will not fix this. The orchestration protocol, data contracts, persistence ownership, and dependency graph must change.

## Target module topology

Use a deliberately small Maven reactor. Technical separation is expressed by packages inside `core`; only business domains are placed under `modules/`:

```text
personal-ai-parent
├── core
│   ├── public versioned SPI used by extensions
│   ├── conversation/session orchestration
│   ├── extension catalog and tenant capability resolution
│   ├── authorization, idempotency and unit validation
│   ├── event/movement ledger and PostgreSQL persistence
│   └── OpenAI, WhatsApp and observability adapters
├── modules
│   ├── expense
│   │   └── accounts, expenses, income, transfers, liabilities, assets and reports
│   └── saree-work
│       └── employees, material custody, production, inspection and wages
└── application
    └── Spring Boot composition root
```

This can be introduced gradually. First create logical boundaries and architecture tests in the existing artifact; split physical Maven modules once dependencies point in the correct direction.

## What belongs where

| Concern | Owner |
|---|---|
| WhatsApp/voice transport and delivery retry | Channel adapter |
| Conversation session, pending work, correction/undo protocol | Core |
| Tenant identity, enabled capabilities, authorization | Core |
| Domain selection among enabled extensions | Core runtime |
| Expense/account/saree vocabulary and extraction schema | Owning extension |
| Required fields and next-question policy | Owning extension |
| Validation and calculations | Owning extension, deterministic |
| Event identity, evidence, idempotent append and audit | Core |
| Movement atomicity, units and reconciliation protocol | Core |
| Debit/credit meaning and account resolution | Personal-finance extension |
| Material yield, acceptance and wage rules | Saree extension |
| Domain-specific query projections and response templates | Owning extension |
| Cross-extension workflow/report | Explicit integration extension |
| OpenAI SDK, prompting and model telemetry | OpenAI adapter |
| Spring wiring | Application composition root |

The core may know the concepts `event`, `fact`, `movement`, `quantity`, `unit`, `actor`, `resource`, `container`, and `observation`. It must not know `expense`, `account`, `credit card`, `saree`, `thread`, or `wage`.

## Extension contract

Publish a small API and keep implementation details private. Prefer composition of narrow capabilities over one large interface:

```java
public interface BusinessExtension {
    ExtensionDescriptor descriptor();
    Collection<EventCapability> events();
    Collection<QueryCapability> queries();
}

public interface EventCapability {
    EventTypeId type();
    ExtractionContract extraction();
    CompletionDecision evaluate(ProposedEvent event, DomainContext context);
    ExecutionPlan plan(ValidatedEvent event, DomainContext context);
    ResponseModel present(EventOutcome outcome, Locale locale);
}

public sealed interface CompletionDecision {
    record Ready(ValidatedEvent event) implements CompletionDecision {}
    record NeedsInput(FieldId field, QuestionModel question) implements CompletionDecision {}
    record Rejected(List<RuleViolation> violations) implements CompletionDecision {}
}
```

Important contract rules:

- IDs are namespaced, such as `finance:expense:v1` and `saree:material-issued:v1`.
- The core passes a generic `Map<FieldId, FactValue>`/JSON document validated against the extension's schema. It does not use a union DTO containing every business field.
- Every fact retains source evidence and confidence. Only deterministic extension code may turn a proposal into an execution plan.
- `ExecutionPlan` contains immutable event metadata, zero or more signed movements/observations, the rule version, and an idempotency key. Extensions cannot write the core ledger directly.
- Every contract declares API compatibility, event schema version, supported locales/units, required permissions, and migration ownership.
- Runtime registration can use Spring beans initially. The architectural contract must not depend on Spring so Java `ServiceLoader` or separately packaged extensions remain possible later.

## Runtime flow

```text
Inbound message
  -> channel-neutral command
  -> resolve tenant + enabled extension versions
  -> deterministic conversation controls
  -> route against only enabled capability summaries
  -> interpret against only the selected capability schema
  -> sanitize evidence and validate schema in core
  -> extension evaluates completeness
       -> NeedsInput: core stores generic pending event and asks extension question
       -> Rejected: core returns safe domain error
       -> Ready: extension creates deterministic ExecutionPlan
  -> core authorizes and atomically commits event + movements/observations
  -> extension presents typed result
  -> channel adapter delivers response
```

Use two-stage interpretation: first select a capability from small descriptors, then extract using only that capability's schema. This avoids one ever-growing prompt and prevents fields from one domain leaking into another. If routing confidence is low or multiple extensions match, clarify the business context; never execute both.

## Business-type composition

Introduce these core records:

```text
Tenant
TenantProfile(profile_id, tenant_id, profile_type, locale, timezone)
ExtensionInstallation(tenant_id, extension_id, extension_version, status, config_version)
ExtensionConfiguration(tenant_id, extension_id, version, effective_from, config_json)
```

`profile_type` is onboarding/catalog metadata, not a switch used by core execution. An onboarding template such as `SAREE_JOB_WORK` installs a tested set of capabilities and starter configuration. After installation, runtime behavior comes solely from `ExtensionInstallation` and capability contracts.

Examples:

| Business profile | Enabled extensions | Configuration |
|---|---|---|
| Personal expense | `personal-finance` | INR, account aliases, categories |
| Saree job work | `saree-job-work`, `payments` | metre/piece/gram units, wage rates, tolerances |
| Grocery shop | `inventory`, `sales`, `payments` | SKU units, tax/rate rules, reorder thresholds |

For interaction between capabilities, use published domain events and an explicit integration module. Example: `saree-payments-integration` translates an approved wage payable into a payment request. Saree must not import personal-finance internals.

## Persistence boundary

The detailed, normative decision for future implementations is maintained in
[extension persistence rules](extension-persistence-rules.md). New extensions and coding agents must apply
those ownership, projection, migration, and legacy-table rules.

Replace `state_change`/`state_mutation` as the long-term core abstraction with an append-only generic ledger:

```text
core_event
  id, tenant_id, extension_id, event_type, schema_version,
  occurred_at, recorded_at, actor_id, status, facts_json,
  evidence_json, raw_input_ref, rule_version, idempotency_key, causation_id

core_movement
  id, event_id, resource_id, container_id, quantity, unit_id, direction

core_observation
  id, event_id, subject_type, subject_id, value, unit_id, observed_at
```

Add unique `(tenant_id, idempotency_key)` and validate compatible units before commit. A movement is quantitative and unit-aware; finance debit/credit labels are extension semantics. Domain tables and read models remain extension-owned, for example `fin_account`, `fin_expense_projection`, `saree_batch`, and `saree_wage_statement`.

Use one database initially, with table prefixes or PostgreSQL schemas and separate Flyway locations per module:

- `db/migration/core`
- `db/migration/finance`
- `db/migration/saree`

Do not place arbitrary extension data in the core schema merely because JSONB is available. `facts_json` preserves the immutable event contract; queryable domain state belongs in extension projections.

## Migration plan

### Phase 0 — Freeze boundaries and add guardrails

1. Record this decision and define forbidden dependency rules.
2. Add ArchUnit tests: `core` and `conversation` cannot depend on `finance`; extension packages cannot depend on each other; adapters cannot be imported by domain code.
3. Add characterization tests around current expense/account/query behavior and idempotency before moving code.
4. Rename the application description and remove architecture claims that do not yet match implementation.

**Exit:** CI detects any new core-to-finance dependency; existing behavior is protected.

### Phase 1 — Introduce the extension API inside the current artifact

1. Add `extension.api`, `extension.runtime`, and generic fact/evidence value types.
2. Implement an `ExtensionCatalog` and tenant-aware `CapabilityResolver`.
3. Wrap current expense, account, payment, asset and finance-query handlers behind `EventCapability`/`QueryCapability` adapters.
4. Replace the direct `ExpenseHandler` branch and string-keyed `SpeechHandler` map with a single dispatcher.
5. Move finance message rendering and numeric parsing behind finance capabilities or generic unit parsers.

**Exit:** `UnifiedConversationEngine` has no finance import, finance event name, or finance field name.

### Phase 2 — Make interpretation schema-driven

1. Replace `EventFields` with generic facts plus per-capability JSON schema validation.
2. Move the finance prompt, intent vocabulary, examples and field descriptions into `extension-personal-finance` resources.
3. Split routing from extraction and supply only tenant-enabled descriptors/schemas.
4. Store pending events as `{extensionId, eventType, schemaVersion, facts, unresolvedFields, evidence}`.
5. Add collision, low-confidence, disabled-extension and malicious-field tests.

**Exit:** a synthetic non-finance extension can route, extract and ask a follow-up without a core edit.

### Phase 3 — Establish the generic ledger

1. Introduce the new event/movement/observation tables alongside existing tables.
2. Have finance capabilities produce `ExecutionPlan`; dual-write through one core transaction while comparing old and new results.
3. Build finance projections from committed core events and reconcile them with current queries.
4. Backfill old rows with explicit mapping/version metadata; quarantine rows that cannot be mapped safely.
5. Switch reads, stop old writes, observe, and only then retire old tables in a later reversible release.

**Exit:** core persistence contains no expense query or finance mutation semantics, and retries cannot duplicate an event or movement.

### Phase 4 — Split physical Maven modules

1. Extract kernel, API, core, adapters, finance extension, test kit, and application modules.
2. Keep JPA entities and repositories package-private within their owning adapter/extension where practical.
3. Make the application module the only Spring composition root.
4. Run unit, extension-contract, architecture, integration and migration tests in CI.

**Exit:** Maven cannot compile if core imports finance or one extension imports another.

### Phase 5 — Add saree job-work as the second real extension

Implement a thin vertical slice first:

1. register employee and material resources;
2. issue thread with metre movement;
3. record partial saree surrender with piece/gram observations;
4. accept production and calculate effective-dated wages;
5. query custody, production and payable projections.

The implementation may extend the published API but must not add saree nouns or conditionals to core. Any missing generic concept should first be tested against both finance and a small third fixture (for example grocery stock) before entering the core contract.

**Exit:** finance can be disabled and saree workflows still operate; enabling saree for one tenant does not expose it to another.

## Testing and governance

Maintain these test layers:

- **Core tests:** routing isolation, authorization, idempotency, units, atomic commit, correction and audit.
- **Extension contract tests:** descriptor/schema compatibility, deterministic validation/planning, localization and forbidden direct ledger writes.
- **Extension tests:** business rules and projections without a model call.
- **Semantic evaluations:** multilingual routing/extraction per extension, including vocabulary collision and pending-context cases.
- **End-to-end tests:** channel to committed ledger and response, with duplicate and concurrent delivery.
- **Migration tests:** upgrade from the oldest supported schema and data reconciliation totals/counts.

Review every proposed core field with this test: “Can two unrelated extensions explain this without using either domain's nouns?” If not, it belongs in an extension. Promote a concept into core only after at least two real extensions need the same semantics, not merely similarly named data.

## Principal risks and controls

| Risk | Control |
|---|---|
| A “generic” core becomes a hidden ERP | Keep the core contract small; require two-extension evidence for additions |
| One giant manifest becomes a low-code framework | Manifest describes contracts; complex rules remain versioned deterministic code |
| Runtime reflection hides broken compatibility | Validate manifests at startup and installation; use typed SPI and contract tests |
| JSONB becomes an unqueryable dumping ground | Immutable facts in core; extension-owned relational projections for queries |
| Cross-extension transactions become inconsistent | Explicit integration capability and one core `ExecutionPlan` transaction boundary |
| LLM routes to the wrong business domain | Filter by tenant installations, two-stage routing, confidence threshold and clarification |
| Big-bang migration damages finance behavior | Strangler adapters, characterization tests, dual-write comparison and reversible cutover |
| Premature microservices add operational failure modes | Keep a modular monolith until independent scaling/deployment has measured value |

## Definition of complete segregation

Segregation is complete when all of the following are true:

1. Core builds and its tests run without the personal-finance extension on the classpath.
2. No core source, prompt, table, API, or response type contains finance or saree vocabulary.
3. Installing or disabling an extension is tenant-scoped and audited.
4. A new extension adds schemas, rules, migrations, projections, prompts, reports and tests without modifying core.
5. Only core commits generic events/movements; extensions cannot bypass authorization or idempotency.
6. Finance and saree run together without field, prompt, query, table, or handler collisions.
7. A third minimal extension passes the same public contract test suite.

## Implementation status

Implemented in the current modular-monolith cut:

- `core` owns the extension contract, tenant installation/audit, conversation runtime, technical adapters, and append-only generic ledger.
- technical concerns remain separated by packages but are intentionally not separate Maven modules.
- tenant capability resolution filters routing, schemas, prompts, context, events, and queries by installation state.
- finance handlers are reached through `PersonalFinanceExtension`; the unified conversation engine has no finance dependency.
- saree job work is a standalone module covering employee registration, material custody, surrender, acceptance, wage accrual/payment, and operational queries.
- interpretation is two-stage: tenant-enabled capability routing followed by extraction against only the selected extension schema.
- finance expense reads use the extension-owned `fin_expense_projection`, populated idempotently from committed `core_event` records.
- startup compatibility/collision checks and ArchUnit dependency rules enforce the boundary.

The old finance state tables remain an extension-owned compatibility projection while existing finance flows are strangled onto `core_event`. They are not the platform ledger and should be removed only after all legacy read models have been replaced and reconciled.
