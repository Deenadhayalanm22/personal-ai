# Explanatory observation cards

Observation remains a level, not a statement about the user's financial habits. Patterns
retain their existing evidence thresholds. This change replaces generic three-card observations
with one card explaining the available records.

## Example grounded in the September regression

**One household purchase explains most of 10 Sep**

₹5,445 at Amazon (Household Items) accounted for 89% of the ₹6,107 recorded. The other
four expenses totaled ₹662. The single card has a direct `OPEN_EVIDENCE` action.

**Most recorded food spending went toward groceries and provisions**

Groceries and provisions accounted for ₹13,476 of ₹16,203 recorded food expenses.
The remainder was ₹2,727. The two records without spending-nature classification remain
included in this total and carry an incomplete-classification note.

Parents support remains ₹20,500 across three entries. Two identical-date/amount/classification
entries are flagged for review, not removed. Their presence lowers that candidate's priority;
the system does not claim they are accidental duplicates.

## Responsibilities

- `MoneyStoryObservationFactory`: derives contributor, subcategory breakdown, purpose,
  repeated-merchant or minimal summary facts from active confirmed expenses. Produces candidates
  for all observed days/categories/merchants so alphabetical selection cannot discard a useful fact.
- `MoneyStorySelector`: prefers supported patterns, then informative observations. A concrete daily
  contributor outranks a broad discretionary contributor. Suppresses the same subject, highly
  overlapping evidence, multiple contributor stories and empty summary padding. May return fewer
  than three stories. At least one factual fallback remains available for a single valid expense.
- `MoneyStoryRenderer`: deterministic one-card observation copy. No AI calls for observations.
  Counts, amounts and labels come from the structured facts. No invented habits, affordability
  judgments, recipient relationships beyond classification, or spending recommendations.
- `MoneyStoriesService`: reads source expenses for observations and aggregate context for patterns;
  verifies evidence, snapshots the explanatory facts, hashes subcategories/classifications as well
  as amounts, and preserves saved display order in internal and public API responses.

Partially classified records are not silently discarded from observation totals. Discretionary
observations use only explicitly classified discretionary records and state their scope. Historical
patterns are withheld when the fetched comparison history has incomplete classification; thresholds
have not been lowered. This is a conservative data-quality guard, not a completeness guarantee.

## API / persistence

- Observation `cards` contains one existing `HERO_STAT` card with an inline `OPEN_EVIDENCE` action.
  Pattern card layout remains unchanged. Clients must use the returned card count rather than
  assume three cards. Compact face headings remain <=20 characters.
- `observation` contains the kind, subject, focus/total/remainder/share, subcategory parts, evidence
  IDs, focus IDs and data-quality counts. The arithmetic and evidence can be independently checked.
- Public API now includes `level`, `logicalStoryId`, `revision`, `updatedReason`, and `observation`.
- Evidence includes `subcategoryLabel`. No raw message content is exposed.
- Template/calculation version is 3. Observation rule version is 3; pattern rule version stays 2.
- V9 adds `money_story.display_order` and `money_story_evidence.subcategory_label` and marks current
  snapshots stale. Normal generation refreshes them; historical snapshots are retained.

## Regression coverage

`september-observation-regression.json` preserves all 28 user-shared records, including two null
classifications, three equal electricity entries and two equal parents-support entries. Unknown
merchant names use neutral test labels; known Amazon/KK shop labels are preserved.

`MoneyStoryObservationTest` checks numeric explanations, repeat handling, deterministic one-card
rendering, selection, single-entry fallback, unclassified fallback and dominant-purchase deletion.
`MoneyStoriesEvidenceLadderIT` replays the varied household's 180 user-days and checks one-card
structure, evidence totals, breakdown arithmetic, distinct subjects and existing correction/idempotency
contracts. A separate real-capture September test checks the public API and persisted evidence.

## Verification

A clean run (so no stale compiled migrations or tests are reused) passed:

```sh
./mvnw -q clean -Dspring.profiles.active=test '-Dtest=*Test,*Tests,MoneyStoriesEvidenceLadderIT,MoneyStoriesIT' test
```

95 tests, 0 failures, 0 errors. This includes 20 focused evidence-rule tests, 8 observation
regressions, all 3 sixty-day users, the old-date worker correction, the September public-API
regression and the four-day capture regression. No production database was modified by the run;
integration tests use the configured disposable local test database.
