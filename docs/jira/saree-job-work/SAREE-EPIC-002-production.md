# SAREE-EPIC-002 — Material issue, production, and surrender

| Field | Value |
|---|---|
| Parent | INIT-003 |
| Status | To Do |

## SAREE-005 — Issue thread to an employee

**Status:** To Do · **Priority:** P0

**Example:** “Give Selvi 1,000 metres red thread today.”

### Acceptance criteria

1. Employee, positive quantity, unit, and effective date create an issue batch with a unique reference.
2. Known owner stock decreases and employee-held material increases atomically; unknown stock does not become zero.
3. Insufficient known stock blocks the issue or follows an explicitly configured override approval; it never silently becomes negative.
4. The reply states employee, actual quantity/unit, date, batch, and expected theoretical output if configured.
5. Duplicate delivery does not issue material twice.

## SAREE-006 — Record partial saree surrender

**Status:** To Do · **Priority:** P0

**Example:** “Selvi returned 24 sarees from the red batch.”

### Acceptance criteria

1. Employee, positive piece count, date, and batch (or unambiguous open batch) record a surrender.
2. One batch accepts multiple partial surrenders across days/weeks without closing prematurely.
3. If several open batches match, the assistant asks one identifying question before recording.
4. Received output is pending inspection until the configured acceptance rule is satisfied.
5. Duplicate delivery does not add the sarees twice.

## SAREE-007 — Inspect and accept production

**Status:** To Do · **Priority:** P0

### Acceptance criteria

1. The owner can accept all pieces or record accepted, rejected, rework, and optional measured weight counts that sum to surrendered pieces.
2. Only accepted pieces increase saleable finished stock and wage eligibility.
3. Rejected/rework pieces remain traceable and do not disappear or become an automatic wage deduction.
4. Weight outside configured tolerance produces a warning requiring owner disposition, not automatic rejection.

## SAREE-008 — Record material return, scrap, and close a batch

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. Unused thread returned by the employee moves back to owner stock with actual quantity/unit.
2. Scrap/wastage requires quantity, unit, date, reason, and authorized confirmation.
3. A batch closes only after the owner confirms no more production/returns are expected.
4. Closure reports issued, returned, recorded consumption/variance, accepted/rejected output, theoretical yield, and unresolved discrepancies without treating theory as fact.

## SAREE-009 — Correct production records safely

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. Issue, surrender, inspection, return, and scrap corrections preserve original history and compensate affected stock/wage entries.
2. A correction after wage payment shows the payable difference and requires owner confirmation; it does not rewrite a settled payment silently.
