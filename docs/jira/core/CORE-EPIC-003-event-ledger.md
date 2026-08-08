# CORE-EPIC-003 — Generic event and movement ledger

| Field | Value |
|---|---|
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Store simple quantities and credits/debits safely across unrelated domains |

## CORE-011 — Store actors, resources, containers, and units

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. Every record is tenant-owned and has a stable identifier, lifecycle status, display name, and extension type.
2. A container holds one resource in one canonical unit; incompatible units cannot be combined.
3. Unknown quantity remains unknown and is never changed to zero.
4. Extension metadata is schema-validated and cannot replace fields required for ownership, unit safety, or auditing.

## CORE-012 — Record immutable events and observations

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. An event retains type/version, actor, effective time, recorded time, source/channel, original evidence reference, structured fields, status, and extension version.
2. A valid observation can exist before all optional enrichment is known.
3. Corrections supersede or amend facts without physically deleting audit history.
4. Sensitive raw content follows extension-independent retention/redaction policy.

## CORE-013 — Commit balanced movement plans exactly once

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. Each movement records resource, unit, signed quantity, source/destination or reason, causal event, and rule version.
2. The same idempotency key cannot commit the same event or plan twice, including under concurrency.
3. Multi-movement events commit atomically; partial success is impossible or recoverable without duplication.
4. Replaying a committed event produces the same ledger state.
5. “Balanced” means conservation within an extension-declared transfer/conversion plan; conversions may consume one resource and produce another with explicit variance.

## CORE-014 — Reconcile physical or monetary counts

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. A user can record a dated counted quantity/value as a reconciliation observation.
2. The system shows expected, counted, and variance without silently rewriting prior movements.
3. An extension decides whether confirmed variance creates an adjustment movement and who may approve it.
4. Backdated reconciliation triggers deterministic recalculation or an explicit unsupported/conflict response.

## CORE-015 — Query a transparent audit history

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. Authorized users can trace a current quantity to reconciliation/opening facts and subsequent movements.
2. Queries preserve unit and extension boundaries and disclose incomplete/unreconciled scope.
3. Event, correction, reversal, and reconciliation histories are understandable in localized plain language.
