# EPIC-006 — AI quality, multilingual experience, and cost

| Field | Value |
|---|---|
| Issue type | Epic |
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Deliver useful English, Tamil, and romanized Tamil conversations with measurable accuracy, safety, latency, and cost |

## STORY-026 — Use one structured interpretation call per free-form turn

**Status:** Done · **Priority:** P0

**As a** product owner, **I want** one shared semantic interpretation contract **so that** behavior is consistent and model cost is controlled.

### Acceptance criteria

1. **Given** active WhatsApp free text requiring semantic understanding, **when** processed, **then** one primary structured interpreter call is used.
2. **Given** greeting/help, a control command, trusted button, or deterministic pending numeric answer, **when** processed, **then** interpretation can be bypassed safely.
3. **Given** a routine expense query or operational response, **when** the result is known, **then** deterministic execution/rendering avoids legacy classifier and explanation calls.
4. **Given** a low-confidence result, **when** escalation is enabled, **then** only the configured threshold/path may invoke a distinct stronger model.

## STORY-027 — Respond in English, Tamil, and romanized Tamil

**Status:** In Progress · **Priority:** P1

**As a** multilingual user, **I want** the assistant to understand and reply in my language **so that** finance tracking feels natural.

### Acceptance criteria

1. **Given** English, Tamil script, or romanized Tamil input, **when** interpreted, **then** the output identifies `en-IN`, `ta-IN`, or `ta-Latn` as applicable.
2. **Given** routine onboarding, clarification, controls, and expense totals, **when** rendered, **then** reviewed deterministic templates exist for the detected/preferred language.
3. **Given** a user language preference, **when** saved, **then** it persists independently of occasional code-switching and can be changed.
4. **Given** untranslated operational text, **when** a supported language is active, **then** fallback behavior is consistent and observable.

### Sub-tasks

- STORY-027-A — Persist explicit language preference, distinguishing Tamil script and romanized Tamil.
- STORY-027-B — Move remaining account, credit, payment, and expense strings into the message catalogue.
- STORY-027-C — Complete reviewed translations and fallback tests.

## STORY-028 — Establish a reviewed semantic evaluation program

**Status:** In Progress · **Priority:** P0

**As a** product and engineering team, **I want** repeatable semantic evaluations **so that** model or prompt changes are evidence-based.

### Acceptance criteria

1. **Given** consented production-like examples, **when** added to the corpus, **then** they are redacted, reviewed, labeled, and versioned.
2. The evaluation reports event accuracy, unsafe mutation rate, clarification rate, invalid-output rate, calls/tokens per turn, latency, and estimated cost by language/path.
3. Deterministic integration fixtures do not depend on live-model wording or availability.
4. Live-model tests are opt-in, use the configured model, assert semantic and persisted outcomes, and never run accidentally in normal builds.
5. A model, prompt, escalation, retrieval, distillation, or fine-tuning change is adopted only when the agreed evaluation thresholds pass.

### Sub-tasks

- STORY-028-A — Build the reviewed English/Tamil/Tanglish corpus governance process.
- STORY-028-B — Establish baseline metrics and release thresholds.
- STORY-028-C — Add regression reporting suitable for prompt/model comparisons.

## STORY-029 — Measure and reduce AI cost safely

**Status:** In Progress · **Priority:** P1

**As a** service owner, **I want** AI usage measured and minimized **so that** cost reductions do not weaken financial safety.

### Acceptance criteria

1. Metrics expose calls, latency, input tokens, cached input tokens, output tokens, failures, and avoided calls by bounded purpose/model/outcome tags.
2. Phone numbers, messages, user IDs, event IDs, and other sensitive/high-cardinality values never appear in metric tags.
3. The 70% call-reduction objective is measured against a versioned representative baseline rather than anecdotal flows.
4. Taxonomy retrieval, escalation, smaller models, distillation, or fine-tuning is introduced only after evaluation demonstrates net quality/cost value.
5. Batch execution is limited to offline evaluation/insights, not latency-sensitive conversation turns.

## STORY-030 — Govern prompts, models, and structured outputs

**Status:** To Do · **Priority:** P1

**As an** engineering team, **I want** model behavior versioned and recoverable **so that** upgrades do not silently change financial interpretation.

### Acceptance criteria

1. Production model identifiers and prompt/schema versions are recorded with releases and can be rolled back.
2. Structured output schemas reject unknown or invalid financial values at the application boundary.
3. Prompt changes receive code review, semantic evaluation, and security/adversarial tests.
4. Model retirement or upgrade has a documented compatibility and rollout plan.
