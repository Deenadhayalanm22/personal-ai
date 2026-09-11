# Money Stories: evidence ladder acceptance specification

Status: Observation/Pattern MVP implemented from the test-first contract.
The four-day capture regression now expects early observations, not premature patterns.

## Scope and vocabulary

MVP topics have exactly two candidate levels: OBSERVATION and PATTERN. These are
alternative claims, not user ranks. Guidance, budgets, goals and spending limits are
out of scope for this MVP. Stronger evidence can support a pattern; otherwise fall back
to a useful factual observation. If neither is useful, publish nothing.
Keep existing topic enums for compatibility, but observation copy must not imply growth,
concentration, unusualness or habitual behavior when those claims are unsupported.

Use recorded expenses language throughout. Transaction count, account age and entry
frequency do not establish completeness. No empty-day inference of zero actual spending.
No transaction-count unlock prompts. No synthetic entry rewards.

## Frozen v1 acceptance policy (hypotheses, not validated production thresholds)

Periods: user-local ISO weeks, including cross-month weeks; completed calendar months
for growth; actual expense date for day stories. Observation may cover an open period,
but ends at the evaluation date and explicitly says 'so far'. Four completed weeks means
four consecutive full ISO weeks before evaluation, not 28 arbitrary dates.

Candidate evaluation occurs after materialization on every fixture day. This suite tests
availability, not push notifications. Weekly delivery/notification cadence is separate.
Monthly closed-period candidates become available on the third of the next month.
Read-only API calls cannot generate or revise snapshots. A calendar boundary can make
candidates eligible even when no new transaction arrives.

| Existing topic | OBSERVATION | PATTERN |
|---|---|---|
| DISCRETIONARY_FREQUENCY | >=1 discretionary expense; count and sum only | >=3 discretionary purchases on >=2 dates in each of 4 completed weeks; describe recorded recurrence, not overspending |
| CATEGORY_SPENDING_GROWTH | >=1 expense in category; category total, never 'growth' | 2 completed months, >=8 category-active dates spanning >=3 ISO weeks in each; both positive baselines; increase >=25% AND >=₹500 with current >=₹1,000 |
| WEEKEND_SPENDING_PATTERN | >=1 weekend expense; weekend amount only | 4 completed weeks, each with >=1 weekend and >=2 weekday expense dates; pooled weekend share >=35%, amount >=₹1,500 |
| MERCHANT_CONCENTRATION | >=1 resolved merchant expense; merchant total/count | >=3 purchases on >=2 dates in each of 4 completed weeks; merchant >=25% of recorded period spending and >=₹1,500 |
| UNUSUAL_HIGH_SPEND_DAY | >=1 expense on day; total, no 'unusual' label | Previous 8 weeks relative to the DAY (exclude target day), >=15 active dates across >=4 weeks; day >=₹2,000 AND >=2.5x active-day median |

Median of an even sample is the average of the two middle values. Ratios use ×, shares
use %. New/zero-baseline categories remain observations. These gates license statements
about recorded data only; completeness claims need a separate trustworthy coverage source.

## Test-visible candidate contract to implement

Expose a candidate evaluation seam separate from selected feed. Each candidate needs:
stable logical key, topic, subject, level, actual period, structured metrics, exact evidence
IDs, baseline window/evidence IDs and eligibility reason codes.
Selected StoryDto adds `level`, `logicalStoryId`, `revision`, `updatedReason`.
Tests must inspect structured values, not match LLM prose. Copy generator is mocked to
return deterministic fallback. Grounded copy tests are separate from financial calculations.
The current monthly endpoint remains the read path for the initial red integration tests;
each topic rule is independently testable through `evaluate(StoryEvaluationContext)`.

Select <=3 non-overlapping stories per monthly collection. Prefer supported patterns,
then useful observations; within a level use newest period, then topic and subject
lexical order. Do not show multiple levels of
the same topic/subject/period. Suppression by ranking must not be mistaken for ineligibility.
An unchanged input + evaluation period must preserve logical identity/revision.

## Explicit 60-day fixture

`src/test/resources/money-stories/sixty-day-story-fixture.json` enumerates every day for
every user, including empty days. 6 July–3 September 2026 = 60 days = 8 weeks + 4 days.
Entry time is the mutable clock at 10:00 Asia/Kolkata; occurredOn is independently supplied.
Jobs run at 23:00 daily after rebuilding all affected expense dates, including old dates.
Repeat the story job without rebuilding to test no-op idempotence. Do not sleep or alter OS time.

- A: ₹500 delivery on days 1,3,6,19,35,52. Day 8 changes day-3 amount to ₹200;
  day 9 deletes day-6 expense. Sparse observations, never pattern.
- B: enters three ₹500 delivery expenses on day 26 (July 31), dated July 7,12,19;
  repeats on day 57 (August 31), dated August 7,12,19. Zero stories before first entry.
  Three active expense dates per month, not a month of coverage. Historical observation
  appears immediately after backfill, but no growth/pattern.
- C: groceries ₹200 daily; delivery ₹300 on July weekdays, ₹450 on August/September
  weekdays and ₹600 on weekends. Day 50 adds ₹6,000 electronics. Day 59 moves that expense
  from August 24 to July 20; both months and dependent baselines must refresh.
  No baseline seeded before day 1. No user is assigned a readiness class in production.

## Checkpoint oracle (candidate checks, independent of feed suppression)

| Checkpoint | A | B | C |
|---|---|---|
| Day 1 | Delivery observation ₹500 | No entries/stories | Food total ₹500; discretionary ₹300; no pattern |
| Day 6 | Three food entries ₹1,500 | Empty | Weekend observation; no recurring-weekend claim |
| Day 7 | Same evidence, no manufactured new discovery | Empty | Week Jul 6–12: groceries ₹1,400, delivery ₹2,700, combined ₹4,100; still no 4-week pattern |
| Day 8 | Revised July total ₹1,200 | Empty | New week cannot inherit first week's evidence |
| Day 9 | Revised July total ₹700, deleted entry absent | Empty | No baseline growth claims |
| Day 26 | July observed total ₹1,200 | July observation ₹1,500 from 3 old dates | July still open: no full-month growth |
| Day 29 (Aug 3) | Observation only | Observation only | Four completed weeks: delivery frequency and merchant patterns; weekend share 6400/16400 = 39.02% |
| Day 50 | Observation only | No new entry; no invented current spending | Aug 24 total ₹6,650 vs trailing active-day median ₹650 = 10.23×; outlier PATTERN |
| Day 57 | Observation only | August observation ₹1,500; two entry sessions do not imply complete months | August completed only at end of day; no premature monthly growth publication |
| Day 59 | No impact from C's edit | No impact from C's edit | Move spike: remove Aug-24 outlier claim; refresh July and dependencies |
| Day 60 (Sep 3) | Observation only | No category-growth PATTERN | Food July ₹14,800, August ₹21,650: +₹6,850 / +46.28%; CATEGORY_SPENDING_GROWTH PATTERN |

July has 26 tracked dates and August 31; comparisons must say recorded totals, never
prove a behavioral change. Monthly active-date gates don't imply complete coverage.

## Evidence transitions and entry-frequency counterfactuals

For each of the five topics, test an OBSERVATION with sparse valid evidence and a PATTERN
with all of that topic's requirements satisfied. Removing qualifying evidence must downgrade
the same logical topic/subject/period to OBSERVATION, or retract it if no useful evidence remains.
Do not create a new discovery merely because its level changed after a correction.

B with a complete 28-date historical backfill (counterfactual fixture) can qualify for
four-week patterns in one entry session. C with insufficient topic-specific history must
remain at OBSERVATION for that topic despite daily app usage. Entry frequency is not a gate.

## Focused acceptance suite beyond the longitudinal replay

1. Parameterized 5-topic × 2-level candidate matrix, with exact metrics and evidence IDs.
2. Every gate: just below / exactly at / above count, date coverage, amount and ratio thresholds.
3. No-data / one-entry / zero previous total / no merchant / uncertain category / invalid currency.
4. Four weeks vs 28 expenses on one date; one bulk session vs identical same-day-entered history.
5. Partial current month never compared to full previous month; completed-week gates before/after
   local midnight; week spanning July/August; leap February; user timezone independent of server.
6. Pending and unconfirmed drafts excluded; same confirmed message replayed without duplicate count.
7. Exact evidence: weekend excludes weekdays; merchant excludes other merchants; all evidence is
   owned by user; totals reconcile; comparison evidence remains distinct from current evidence.
8. Viewed/unviewed revisions: small edit updates; material edit labels correction; delete losing
   eligibility retracts; no-op edit preserves revision; read/dismiss state survives correction.
9. Baseline-only edit refreshes all dependent stories, including windows beyond next month; moving
   a date invalidates old/new periods; deletion of last expense cannot preserve stale evidence.
10. Rank candidates independently: strongest supported version only; <=3, deterministic ties;
    no repeated story promoted as new; suppressed valid candidates still testable via evaluator.
11. Failure during refresh retains last good version as explicitly stale; no partial deck/evidence;
    retry yields one revision; concurrent jobs yield one current snapshot.
12. Clock-only eligibility changes trigger evaluation; unchanged rerun does not create revisions;
    daily aggregation snapshot and source evidence must describe the same input revision.

## Implementation/test staging and honest red status

Stage 1 (included): explicit 180 user-days + runnable parameterized integration replay using
real capture/confirmation, mutable Clock, real aggregation/scheduler, corrections, daily
contract/integrity checks, rerun identity checks, and numeric checkpoint source-ledger oracle.
The `level` contract is implemented. A separate regression also verifies that the scheduled
worker rebuilds historical corrections without the test manually rebuilding aggregates.

Stage 2: the candidate-level 10-cell matrix, sparse-backfill suppression, open-month gate,
median and moved-outlier regression are implemented in `MoneyStoryEvidenceRulesTest`.
Remaining acceptance designs (not yet executable): exhaustive threshold boundaries, evidence upgrade/downgrade,
read/dismiss lifecycle, concurrency/failure and boundary suites need new public seams. Do not
claim those tests are implemented or covered by the three-user replay. Implement tests for
these contracts before their corresponding production behavior, without guessing persistence.

Run compile first, then only the new IT against the disposable test database. Existing initializer
cleans the configured test schema: never point it at a development/user database. Infrastructure
failure is not the expected behavioral TDD red. Preserve the legacy IT until migration decisions
are explicit. Its expectations have now been migrated to the Observation/Pattern policy.

## Validation performed

- Before implementation, all three 60-day scenarios failed on behavioral assertions once
  local database access was enabled (3 failures, 0 infrastructure errors).
- `./mvnw -q -Dtest=MoneyStoriesIT,MoneyStoriesEvidenceLadderIT test`: passed all 5 cases
  (3 user journeys, 1 historical-correction worker regression, 1 four-day capture regression).
- `./mvnw -q -Dspring.profiles.active=test test`: passed all 79 tests, including 18 focused
  evidence-rule cases and the application-context smoke test.
- The default profile lacks a JDBC URL in this environment; the test profile supplies the
  local test configuration. Database tests require local network access.
- Fixture totals independently recomputed: C July food ₹14,800; August ₹21,650; first four
  completed weeks ₹16,400. Fixture includes all 60 dates per persona.

## Reading the replay tests

Start with `MoneyStoriesEvidenceLadderIT.storiesReflectTheEvidenceAvailableOnEachDay`.
Its daily loop lists the actions in order, followed by `verifyStoriesForToday`.

- `MoneyStoryScenarioDriver`: test clock, real capture/confirmation, corrections and jobs.
- `MoneyStoryAssertions`: named read-only expectations and independent July food checkpoints.
  Failures accumulate until the journey ends so one missing contract field does not hide later days.
- `sixty-day-story-fixture.json`: editable day-by-day inputs for A, B and C.

This readability refactor preserves the existing replay assertions; it does not implement
any of the still-planned candidate-level acceptance tests.

## MVP implementation notes

- One rule per topic evaluates Observation/Pattern eligibility using recorded expense dates.
- `MoneyStorySelector` orders supported candidates; identical evidence is not repeated in a deck.
- `MoneyStoryRenderer` owns presentation, including ratio-vs-percent formatting and factual observation copy.
- `MoneyStoriesService` coordinates evidence checks and atomic, content-deduplicated snapshots.
- Injected Clock controls evaluation and publication. Date-only eligibility changes are evaluated daily.
- V6 adds evaluation date/content fingerprint; V7 tracks dates requiring aggregate rebuilds after
  confirmed entries, edits, moves and deletes. The story worker drains these before evaluation.
- Per-story logical IDs and revisions are returned. Viewed/dismissed state and retraction links
  remain outside this implementation; do not infer those user-interface behaviors are complete.

## Persisted level

V8 adds `money_story.story_level` as a non-null string enum constrained to OBSERVATION or
PATTERN. `story_type` remains the topic. Existing levels are backfilled from payload JSON;
pre-ladder snapshots retain PATTERN, reflecting the original pattern-only generation rules.
Legacy payloads missing a level are updated to match. The replay asserts that every returned
story has the same level in the dedicated database column and in the API response.
