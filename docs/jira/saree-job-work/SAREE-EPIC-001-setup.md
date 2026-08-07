# SAREE-EPIC-001 — People, materials, and configurable standards

| Field | Value |
|---|---|
| Parent | INIT-003 |
| Status | To Do |

## SAREE-001 — Register an employee conversationally

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. Friendly name or unique local identifier is sufficient to create a worker; phone/address are optional and privacy-minimized.
2. Similar names trigger a short disambiguation before issue, surrender, wage, or payment is posted.
3. Inactive employees remain in history but cannot receive new material without reactivation.

## SAREE-002 — Register raw and finished resources

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. Raw thread is tracked in a declared canonical unit (initially metre) and finished sarees in pieces; weight observations use grams.
2. Material type, colour, lot, supplier, and quality grade are optional extension fields and do not fragment the core schema.
3. Unit conversions require configured deterministic factors; unknown conversions require clarification.

## SAREE-003 — Configure effective-dated production standards

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. The owner can configure standard issue quantity, nominal length/weight, expected weekly range, wage rate, tolerances, and effective date.
2. Changing a standard affects new work only; historical calculations retain the rule version used.
3. The system displays `1000 / 6.25 = 160` as theoretical yield and clearly distinguishes it from actual yield.
4. No wastage tolerance or deduction is assumed until explicitly configured and confirmed.

## SAREE-004 — Record opening stock through reconciliation

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. The owner can say the counted raw-thread and finished-saree stock with an effective date.
2. Unknown stock remains unknown; the system can record subsequent movements without claiming exact stock.
3. Later recount shows expected, counted, and variance and requires confirmation for an adjustment.
