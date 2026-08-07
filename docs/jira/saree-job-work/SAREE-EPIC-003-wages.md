# SAREE-EPIC-003 — Weekly wages and settlement

| Field | Value |
|---|---|
| Parent | INIT-003 |
| Status | To Do |

## SAREE-010 — Calculate weekly payable wages

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. For each employee and configured week, payable pieces equal accepted, not previously paid pieces with acceptance date in the period.
2. Base wage equals payable pieces × the effective-dated per-piece rate; at ₹100, 20 pieces yield ₹2,000 and 30 yield ₹3,000.
3. Rejected, rework-pending, duplicate, and already-paid pieces are excluded and itemized.
4. Manual bonus/deduction requires amount, reason, owner confirmation, and audit; productivity shortfall never creates an automatic deduction.

## SAREE-011 — Review and approve a weekly wage statement

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. The owner can ask “What should I pay this week?” and receive employee, accepted count, rate, adjustments, total, and unpaid prior balance.
2. Ambiguous week boundaries use the tenant's configured timezone/week start and are stated in the reply.
3. Approval freezes a versioned statement; later production corrections generate an adjustment rather than changing it invisibly.

## SAREE-012 — Record full or partial payment

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. Employee, positive amount, date, and payment method record payment against one or more approved wage statements.
2. Full payment marks covered payable lines settled; partial payment leaves a transparent balance.
3. Cash/bank balance integration is optional and belongs to an explicit composition with the personal/business finance extension.
4. Duplicate payment messages do not pay twice; overpayment requires confirmation and creates a visible advance balance if allowed.

## SAREE-013 — Provide employee wage history

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. The owner can query earned, paid, adjusted, and outstanding wages by employee and period.
2. Each amount traces to accepted pieces, rate version, adjustment, and payment.
3. The employee can receive their own statement only after an identity/permission policy is implemented.
