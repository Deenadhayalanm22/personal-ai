# FIN-EPIC-001 — Personal accounts and financial capture

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | In Progress |

## FIN-001 — Set up a personal account

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. Account type and friendly name permit creation; balance, safe reference, credit limit, and due day are optional.
2. Currency defaults to INR for the current profile when omitted.
3. Cash, bank/UPI, credit, loan, and asset account types expose their own valid attributes.
4. No missing opening balance is converted to zero.

## FIN-002 — Record an expense progressively

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. Positive amount and expense meaning record the known activity before optional account enrichment.
2. Category, date, merchant, notes, source account, and original text are retained where supplied.
3. Missing payment source asks one high-value Cash, Bank/UPI, Credit, or Skip question.
4. A provisional account may link activity but cannot receive a balance mutation until its value is known.

## FIN-003 — Record income and refunds

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. Salary, customer receipt, refund, or other incoming money is recorded as income, not a negative expense.
2. A known destination increases exactly once; an unknown balance keeps the activity without inventing a value.
3. Refund linkage to original spending is retained when identified.

## FIN-004 — Record transfers and liability payments

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. Owned-account transfers are excluded from income/expense totals and adjust both sides atomically.
2. Card-bill or loan payments are liability payments, not expenses; funding and outstanding liability decrease correctly.
3. Ambiguous accounts, same source/destination, invalid amount, or policy violation produces no partial movement.

## FIN-005 — Record asset buys and sells

**Status:** In Progress · **Priority:** P2

### Acceptance criteria

1. A buy decreases funding and increases asset quantity; a sale does the reverse atomically.
2. Insufficient quantity or unresolved accounts prevent all movements.
3. Repeated asset references do not create duplicate owned-asset containers.
