# EPIC-003 — Financial event coverage

| Field | Value |
|---|---|
| Issue type | Epic |
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Support the financial events users commonly describe while preserving deterministic accounting |

## STORY-011 — Record expenses

**Status:** Done · **Priority:** P0

**As a** user, **I want** to record spending with amount, date, category, merchant, notes, and source account **so that** I can track where money goes.

### Acceptance criteria

1. **Given** a valid positive expense amount, **when** recorded, **then** the transaction preserves original text and all supplied structured fields.
2. **Given** date is omitted, **when** the expense is recorded, **then** the configured user/channel date policy is applied consistently.
3. **Given** optional fields are missing, **when** saved, **then** completeness reflects the missing information without losing known facts.
4. **Given** category language varies, **when** normalized, **then** it maps to the maintained taxonomy or remains unresolved; no arbitrary category silently becomes canonical.

## STORY-012 — Record incoming money

**Status:** In Progress · **Priority:** P0

**As a** user, **I want** to record salary, refunds, and other incoming money **so that** account activity and balances include credits.

### Acceptance criteria

1. **Given** current-message evidence of incoming money, **when** authorized, **then** an INCOME transaction is stored exactly once.
2. **Given** a known destination balance, **when** income is applied, **then** the destination increases by the amount and an audit mutation is created.
3. **Given** an unknown destination balance, **when** income is linked, **then** activity is stored without inventing a balance.
4. **Given** a refund references prior spending, **when** supported, **then** the relationship is retained rather than losing it as generic income.

### Sub-tasks

- STORY-012-A — Complete deterministic structured-event execution for INCOME.
- STORY-012-B — Define and test refund linkage semantics.

## STORY-013 — Pay a loan or credit liability

**Status:** In Progress · **Priority:** P0

**As a** user, **I want** to record a credit-card or loan payment **so that** both my funding account and debt remain accurate.

### Acceptance criteria

1. **Given** “pay my card bill” or “pay my loan EMI,” **when** interpreted, **then** it is a LIABILITY_PAYMENT, not an EXPENSE.
2. **Given** valid funding and liability containers, **when** payment executes, **then** funding decreases and outstanding liability decreases exactly once.
3. **Given** either account is ambiguous, **when** payment is requested, **then** the assistant asks for clarification and does not mutate either account.
4. **Given** the payment would violate an explicit account policy, **when** validated, **then** both sides remain unchanged and the user receives a clear reason.

## STORY-014 — Transfer between owned accounts

**Status:** To Do · **Priority:** P1

**As a** user, **I want** to transfer value between my accounts **so that** internal movement is not counted as spending or income.

### Acceptance criteria

1. **Given** distinct source and destination accounts and a positive amount, **when** a transfer is confirmed, **then** one linked transfer event creates balanced source and destination mutations.
2. **Given** a transfer, **when** spending/income summaries run, **then** it is excluded from those totals unless explicitly requested.
3. **Given** source equals destination or an account is unresolved, **when** validation runs, **then** no mutation is made.
4. **Given** a retry or partial failure, **when** recovered, **then** neither side is duplicated or left permanently one-sided.

## STORY-015 — Buy, sell, and value assets

**Status:** In Progress · **Priority:** P1

**As a** user, **I want** to track asset quantities, cost, sales, and valuations **so that** I can understand my holdings.

### Acceptance criteria

1. **Given** an asset buy, **when** applied, **then** funding value decreases and asset quantity increases under one causal event.
2. **Given** an asset sale with sufficient quantity, **when** applied, **then** quantity decreases and proceeds increase the destination account.
3. **Given** insufficient quantity or missing account resolution, **when** a sale is attempted, **then** no partial mutation occurs.
4. **Given** repeated references to the same user-owned asset, **when** resolved, **then** duplicate containers are not created.
5. **Given** a valuation, **when** reported, **then** quantity, price source/effective time, and currency assumptions are distinguishable from cash balance.
