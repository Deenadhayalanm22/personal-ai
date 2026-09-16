# FIN-ARCH-001 — Composable story enrichment

| Field | Value |
|---|---|
| Parent | INIT-002 |
| Status | Proposed architecture |
| Scope | All Money Stories, beginning with `MONTHLY_COMMITMENT` |

## Goal

Allow a story to become more useful as a user voluntarily adds relevant planning information, without making any optional module a prerequisite for a useful story. This is an architecture direction only: it does not yet change a story payload, snapshot schema, or user interface.

## Product requirements

1. Every story remains useful with its own core, verified evidence. A user never needs to share salary, an emergency fund, goals, or a future optional module to receive its core story.
2. Optional information is an independent insight lens, not a sequential level. A user who declines salary but provides an emergency fund and goals can receive resilience and goal insights without seeing a salary prompt.
3. An optional input is used only where it is relevant to the specific story. Salary may enrich a monthly affordability view; it must not be inserted into a merchant or category observation merely because it exists.
4. The product may offer a quiet, direct action at the end of a story when one missing input would materially improve that story. It does not track prompt states, repeatedly nudge, or require a user to complete a profile in this release.
5. No contributor may create an account balance, income transaction, expense evidence, or financial estimate from an optional planning input. Ranges must not produce false-precision percentages.
6. The deterministic backend decides qualification, amounts, ratios, evidence, and allowed actions. AI receives only approved facts and may improve explanation, never calculation or qualification.

## Architecture

Story construction is a composition pipeline, not a single linear story ladder:

```text
Core story evidence + eligible independent contributors
  → verified StoryContext
  → qualified insight rules
  → selected insight modules
  → one composed story snapshot and optional copy generation
```

The planned interfaces are deliberately generic across story types:

```java
interface StoryContextContributor {
    StoryType supports();
    Contribution contribute(StoryBuildRequest request);
}

interface StoryInsightRule {
    String key();
    StoryType supports();
    InsightQualification evaluate(StoryContext context);
}
```

`StoryContextContributor` owns only the facts from its domain. `StoryInsightRule` owns the deterministic requirements for using those facts. `StoryComposer` receives the core facts plus all qualified insight modules and creates one final snapshot. It must not first generate a basic story and then repeatedly rewrite it for each newly available input.

### Example: Monthly Commitment

| Insight module | Required verified information | User value |
|---|---|---|
| `COMMITMENT_CORE` | Active loan EMIs and/or active SIPs | Full intended monthly commitment, source evidence, payoff facts. |
| `COMMITMENT_INCOME_RANGE` | Voluntary salary range | Qualitative affordability context only. |
| `COMMITMENT_INCOME_EXACT` | Voluntary exact salary | A deterministic commitment-to-salary percentage. |
| `COMMITMENT_EMERGENCY_RUNWAY` | Future emergency-fund module plus commitments | Months of commitment cover. |
| `COMMITMENT_GOAL_ALIGNMENT` | Future goal module plus commitments | How chosen goal contributions coexist with commitments. |
| `COMMITMENT_RESILIENCE_AND_GOALS` | Emergency fund, goals, and commitments | A combined resilience and planning lens; salary is not required. |

These modules are a capability graph, not `core → salary → emergency fund → goals`. The composer chooses the most relevant qualified modules for the available cards and does not downgrade independent insight because salary is absent.

### Other story types

The same pattern applies across the feed. Only the core evidence and applicable modules change.

| Story type | Core evidence | Examples of future relevant modules |
|---|---|---|
| Spending pattern | Recorded expenses, categories, and dates | Budget, recurring-expense classification, monthly income context where appropriate. |
| Merchant/category observation | Relevant expense evidence | Category budget or a linked goal; normally not salary. |
| Cash-flow outlook | Recorded expenses and planned commitments | Salary, recurring income, emergency fund. |
| Goal progress | Goal contributions | Commitments, income context, investments. |

## Snapshot and refresh direction

The current `monthly_financial_snapshot` remains the canonical deterministic projection for commitment source facts. Future implementation should retain that ownership and add a generic story context/snapshot layer only when more than one story requires it.

Each stored story snapshot should eventually record:

- its deterministic source facts and provenance;
- the applied insight-module keys and their version;
- a context fingerprint derived from relevant facts and rule versions;
- rendered cards, evidence, revision, and generation timestamp.

When a relevant source changes, only affected stories refresh. A salary update can refresh affordability-capable monthly stories; it must not regenerate unrelated merchant stories. A story read does not infer missing data.

## Initial implementation boundary

1. Implement the generic contributor/rule/composer seam for `MONTHLY_COMMITMENT` first.
2. Preserve its current core totals, evidence, and snapshot refresh behavior exactly.
3. Add salary as the first optional contributor only after its deterministic range and exact-salary rules are specified.
4. Extend one normal expense story only after the commitment implementation demonstrates that the seam is clear and testable.
5. Keep user invitations simple: a single optional action may appear only when the current story has a concrete, relevant enrichment available. No `NOT_SEEN`/`SKIPPED`/`PROVIDED` prompt-state model is part of this architecture.

## Acceptance criteria for a future implementation

1. Given only core evidence, a story returns its existing useful core form.
2. Given optional evidence relevant to a story, the story contains only deterministic, qualified enrichment and preserves core values/evidence.
3. Given optional evidence irrelevant to a story, that evidence does not appear in its context, cards, or actions.
4. Given salary is absent but emergency-fund or goal evidence qualifies, the corresponding insight is available without a salary requirement.
5. Given a relevant source changes, only stories whose context fingerprint changes are refreshed.
6. Given a user has not provided optional information, the response remains honest and any optional action is non-blocking.
