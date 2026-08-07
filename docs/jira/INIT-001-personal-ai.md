# INIT-001 — Personal AI conversational finance assistant

| Field | Value |
|---|---|
| Issue type | Initiative |
| Status | In Progress |
| Outcome | Let a person safely record and understand personal finances through ordinary conversation |
| Primary channel | WhatsApp; REST is a supported integration surface |
| Current locale | India; INR default; English, Tamil, and romanized Tamil |

## Problem

Personal finance tools require rigid forms and sustained manual upkeep. A conversational assistant can lower that effort, but an LLM must never become the source of financial truth or authorize a mutation without evidence from the current user message.

## Product principles

1. Record a useful observation before requesting optional enrichment.
2. Keep language understanding probabilistic and financial execution deterministic.
3. Unknown values remain unknown; an absent balance is not zero.
4. Every financial mutation is attributable, auditable, and safe to retry.
5. Ask one short, high-value question at a time and allow the user to skip or stop.
6. Use the database—not conversation text or an LLM response—as financial truth.
7. Do not claim exact balances until the associated container is reconciled.

## State model

| State | User value | Permitted claim |
|---|---|---|
| Recorded | An activity exists with core facts | Totals, category, date, and merchant where known |
| Linked | Activity is associated with an account/container | Activity grouped by account/container |
| Reconciled | Opening/current value is known through a dated fact | Exact current balance or quantity |

## Scope

### In scope

- Conversational capture of financial events and account setup.
- Multi-turn clarification, corrections, cancellation, and multiple events per message.
- Cash, bank/UPI, credit, loan/liability, and asset containers.
- Deterministic balance/quantity mutation and read-only analytics.
- WhatsApp text/audio and REST entry points.
- English, Tamil, and romanized Tamil interpretation.

### Out of scope until explicitly prioritized

- Regulated financial advice, autonomous payments, bank credential storage, or money movement.
- Tax filing, investment recommendations, shared household accounting, and business bookkeeping.
- Exact balance claims for unreconciled accounts.

## Success measures

| Measure | Initial target |
|---|---|
| Duplicate financial mutations from retried input | 0 |
| Unauthorized mutations without current-message evidence | 0 |
| Model calls for routine/trusted paths | 0 |
| Model-call reduction versus the former multi-classifier path | At least 70% on a representative corpus |
| Supported-language semantic accuracy | Baseline first; target approved after reviewed evaluation set exists |
| Financial audit coverage | 100% of applied balance/quantity changes |

## Initiative acceptance criteria

1. **Given** a supported financial message, **when** it is accepted, **then** the user receives a confirmation or one actionable clarification and the stored state matches the response.
2. **Given** the same inbound event is delivered more than once, **when** it is reprocessed, **then** no transaction or financial mutation is duplicated.
3. **Given** the model returns invalid, ambiguous, or unsupported data, **when** authorization is evaluated, **then** no financial mutation occurs and the user receives a safe recovery path.
4. **Given** an account balance is unknown, **when** activity is linked to it, **then** the activity is retained without converting the balance to zero or claiming an exact balance.
5. **Given** a financial result is presented, **when** its value is calculated, **then** the calculation comes from persisted deterministic logic rather than model-generated arithmetic.
6. **Given** production telemetry, **when** it is inspected, **then** calls, latency, tokens, failures, and avoided calls are measurable without exposing message text, phone numbers, or user identifiers in metric tags.

## Release gates

- P0 and P1 stories for the intended release are Done.
- Deterministic unit and integration suites pass; live-model tests are opt-in and semantically asserted.
- Database changes use reviewed migrations and have a rollback/recovery plan.
- Secrets are environment-managed and production endpoints follow least exposure.
- A reviewed multilingual evaluation report establishes quality and cost baselines.

## Risks and decisions needed

- `spring.jpa.hibernate.ddl-auto=update` conflicts with migration-led production safety and must be removed for production.
- The repository proves only limited integration/fuzz infrastructure despite older documentation claiming comprehensive financial invariant coverage; that claim is intentionally not retained.
- User authentication/authorization, data deletion/export, retention, encryption, backup/restore, rate limiting, and webhook signature verification are not proven and are P0/P1 production gaps.
- “Personal financial assistant” must remain distinct from financial advice; user-facing boundaries need explicit product/legal review.
