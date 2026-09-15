# Money Stories backend architecture

## Current implementation (Observation/Pattern MVP)

The implementation now follows [the evidence-ladder test contract](testing/money-stories-tdd-plan.md).
The design below is the original architecture proposal; where it differs, the implemented
MVP uses aggregate-backed rules, scheduled generation only, early observations, gated patterns,
content-deduplicated snapshots and an injected Clock. The worker rebuilds persisted dirty
expense dates before story evaluation. Guidance is out of scope. The renderer keeps sparse
observations deterministic and permits the existing copy generator only for supported patterns.

## Decision

Build Money Stories as a **versioned insight read model** inside the existing Spring Boot modular monolith. It consumes a normalized, domain-neutral `StoryFact` contract. The first provider adapts confirmed expense transactions; future providers can adapt investments, liabilities, income, or other money domains without changing the story API, story persistence, or renderer contract.

Do not make `financial_transaction` the generic financial ledger and do not put story text, dismissal state, or evidence JSON on it. It is the existing confirmed-expense projection. The generic event/movement ledger described in `CORE-EPIC-003` remains the long-term system of record; Money Stories can later add a ledger-backed provider alongside the current transaction provider.

This is deliberately a modular monolith first. The story workload is per-user and period-bounded, and a separate service would add distributed consistency and operational cost before there is evidence it is needed.

## Current-system fit

| Existing concern | Money Stories integration |
| --- | --- |
| `WebFinanceController` + `WebManager` authenticate web requests | Add `GET /api/web/money-stories?month=YYYY-MM` through the same cookie/session path. |
| `financial_transaction` | Read-only input for the `ExpenseTransactionStoryFactProvider`; only rows with `deleted_at IS NULL` are candidates. A row exists only after confirmation, so drafts/pending extractions cannot appear. |
| `AppUserEntity` | Supplies tenant, locale, currency and IANA timezone. Month and week calculations use the timezone captured in each run. |
| `FinancialTransactionEditService` and `FinancialTransactionWriter` | Publish `MoneyDataChanged` after their database transaction commits. The listener marks intersecting story runs stale; it never regenerates synchronously in an edit request. |
| `CORE-EPIC-003` | A future ledger adapter becomes another `StoryFactProvider`; no table-name dependency leaks into rules or presentation. |

The present transaction schema has no transfer, refund, investment, repayment, or pending status. Therefore the first provider includes only active confirmed expense rows and assigns `EXPENSE` as its fact kind. It must not infer absent concepts from category text. When those concepts exist in a future domain, their provider must explicitly classify them as ineligible for the expense-story rules.

## Module boundaries

```text
web controller -> WebManager -> MoneyStoriesQueryService
                                  |
                                  +-> StoryRunService (load/create/refresh snapshot)
                                  |     +-> StoryFactProviderRegistry
                                  |     |     +-> ExpenseTransactionStoryFactProvider
                                  |     |     +-> InvestmentStoryFactProvider (future)
                                  |     +-> StoryRuleRegistry
                                  |     +-> StoryRenderer
                                  |     +-> StorySnapshotRepository
                                  |
transaction create/edit/delete -> MoneyDataChanged -> StoryInvalidationListener
```

### Stable internal contracts

```java
public interface StoryFactProvider {
    String providerKey();                 // e.g. "expense-transaction/v1"
    Set<StoryFactKind> supportedKinds();
    List<StoryFact> findFacts(StoryFactQuery query);
}

public record StoryFact(
    String sourceType,                    // "FINANCIAL_TRANSACTION"
    String sourceId,                      // source primary key, serialized
    StoryFactKind kind,                   // EXPENSE, INVESTMENT_BUY, ...
    StoryFactStatus status,               // CONFIRMED, PENDING, DELETED, ...
    BigDecimal amount,
    CurrencyUnit currency,
    LocalDate effectiveDate,
    String category,
    String subcategory,
    SpendingNature spendingNature,
    String merchantLabel,
    Map<String, Object> attributes,
    Instant sourceUpdatedAt
) {}

public interface StoryRule {
    StoryType type();
    int ruleVersion();
    Set<StoryFactKind> acceptedKinds();
    List<StoryCandidate> evaluate(StoryEvaluationContext context);
}
```

`StoryFact` is an application DTO, not a JPA inheritance hierarchy and not a new universal table. Each provider owns its source query and maps only fields the rule needs. The `attributes` map is additive and schema-validated by the provider; it cannot replace tenant, amount, currency, date, status, or source identity.

Rules operate on `StoryFact`, never repositories or JPA entities. Renderers operate on a numeric `StoryCandidate`, never raw database rows. This keeps calculation, eligibility, copy, and persistence independently testable and prevents new sources from creating special cases throughout the codebase.

## Persistence model

Store an immutable published snapshot plus normalized evidence. The API reads snapshots, so a user sees the exact deck and evidence that were generated together even if a source row is edited later.

```text
money_story_run
  id UUID PK
  user_id BIGINT FK app_user
  scope_month DATE                 -- first day of requested month
  timezone VARCHAR(60)
  locale VARCHAR(20)
  currency VARCHAR(10)
  input_watermark TIMESTAMPTZ      -- max source update included
  generated_at TIMESTAMPTZ
  status ENUM(GENERATING, READY, STALE, FAILED)
  generation_version INT
  unique(user_id, scope_month, generation_version)

money_story
  id UUID PK                       -- API storyId; immutable for this snapshot
  run_id UUID FK money_story_run
  story_type VARCHAR(60)
  rule_version INT
  template_version INT
  period_type VARCHAR(20)
  period_start DATE
  period_end DATE                  -- inclusive, enforced by check
  sort_key VARCHAR(160)
  payload_json JSONB               -- cards and preformatted display fields only
  content_hash CHAR(64)            -- idempotent publishing/deduplication
  unique(run_id, story_type, period_start, content_hash)

money_story_evidence
  story_id UUID FK money_story
  ordinal SMALLINT
  source_type VARCHAR(60)
  source_id VARCHAR(100)
  transaction_id BIGINT NULL FK financial_transaction
  amount NUMERIC(19,2)
  occurred_on DATE
  merchant_label VARCHAR(255)
  category_label VARCHAR(100)
  primary key(story_id, ordinal)
  unique(story_id, source_type, source_id)

```

`payload_json` is a snapshot of the approved frontend envelope, not a flexible rule input. Keeping card data together makes exact replay cheap; normalized evidence supports authorization, correction routing, pagination, and audit queries. A source reference is polymorphic (`source_type`, `source_id`) because not every future money record will be a transaction. The nullable transaction FK is a convenience for today's drill-down only.

Use `NUMERIC(19,2)` initially because the current app only supports two-decimal INR amounts. Before supporting assets/markets that need more precision, introduce a `MoneyAmount(minor_units BIGINT, currency)` or `quantity + unit` value object at the provider/ledger boundary; do not silently round units or prices into this schema.

## Snapshot lifecycle and consistency

1. Request validates `month` exactly as the existing calendar endpoint does, resolves the authenticated user, then derives the user-local month boundaries.
2. A `READY` run with a current input watermark is returned. A stale or missing run is generated on demand under a database advisory lock keyed by `(user_id, month)`; concurrent callers read the completed winner.
3. Generation reads facts in a repeatable-read transaction, evaluates deterministic rules, renders approved cards, writes one new immutable run and its evidence atomically, then marks it `READY`.
4. After a confirmed expense is created, edited, or soft-deleted, `MoneyDataChanged(userId, effectiveDate, changedAt)` is published after commit. Runs whose scope or lookback period intersects the effective date are marked `STALE`.
5. Historical baseline changes invalidate runs that compare against them. The invalidation window is rule-declared (for MVP category growth: edited month plus the following comparable month). Never assume only the current month needs refresh.

If a regeneration cannot complete, retain the last `READY` snapshot and return it with `stale: true`; do not return a half-generated deck. A first-ever failed generation returns a retriable 503 (`MONEY_STORIES_UNAVAILABLE`) rather than fabricated content.

## Rule policy

All thresholds are versioned server configuration, loaded by `StoryRuleRegistry` and stored as `rule_version` on every story. Product must approve the concrete values below before release; they are intentionally configuration, not hidden constants.

| Rule | Inputs | Suggested deterministic eligibility | Evidence |
| --- | --- | --- | --- |
| `DISCRETIONARY_FREQUENCY` | confirmed `EXPENSE`, `DISCRETIONARY` | at least 10 transactions in an ISO week; compare that week's discretionary amount with essential amount in the same week | all qualifying discretionary transactions |
| `CATEGORY_SPENDING_GROWTH` | confirmed `EXPENSE` by category | current month category total is at least configured minimum and exceeds previous comparable calendar month by configured percent and absolute amount | current-month category transactions; comparison amount is a component, not evidence |
| `WEEKEND_SPENDING_PATTERN` | confirmed `EXPENSE` | weekend total reaches configured minimum and configured share of total spend in the selected month | weekend transactions |
| `MERCHANT_CONCENTRATION` | confirmed `EXPENSE` with a resolved merchant | merchant has configured minimum transaction count and spend; its share of category spend crosses configured percentage | merchant transactions |
| `UNUSUAL_HIGH_SPEND_DAY` | confirmed `EXPENSE` | daily total reaches configured minimum and is at least configured multiple of the user's trailing 8 completed-week median daily spend | all transactions on that day |

Global eligibility always requires: same tenant, `CONFIRMED`, active/not deleted, known amount/date/currency, one currency matching the user's selected story currency, and a fact kind accepted by that rule. Transfers, refunds, repayments, investments, and pending facts are excluded by fact kind/status, not by an ever-growing SQL exclusion list.

Define deterministic tie-breakers: period start descending, then impact amount descending, then story type alphabetically. Cap the returned MVP deck (for example, 10 stories) in `StoryRuleRegistry`; keep all generated candidates only if product explicitly needs analytics on suppressed candidates.

## API contract

Implement the supplied `GET /api/web/money-stories?month=2026-09` response envelope unchanged, with these additions that are backward-compatible and operationally useful:

```json
{
  "month": "2026-09",
  "currency": "INR",
  "timezone": "Asia/Kolkata",
  "generatedAt": "2026-09-14T00:15:00+05:30",
  "stale": false,
  "stories": []
}
```

`storyId` is an opaque UUID/string, not a sequence or a transaction identifier. Card content is already ordered but `sequence` remains authoritative. The server emits only the listed enums and validates template/card payloads before publishing. Currency/date/merchant/category labels are generated from the stored run locale/timezone; the frontend never re-formats them.

## Security, performance, and operations

- Every read is constrained by `user_id`; never use a supplied transaction/source ID without resolving it through an owned story.
- Keep raw WhatsApp text out of stories, evidence snapshots, analytics, and logs. Story text should only use approved derived fields (merchant/category/amount/date).
- Add indexes: `money_story_run(user_id, scope_month, status)`, `money_story(run_id, sort_key)`, and `money_story_evidence(story_id, ordinal)`. For current facts, add a partial composite transaction index on `(user_id, occurred_at, spending_nature, category, merchant_id)` where `deleted_at is null` if query plans require it.
- Observe generation duration, providers scanned, candidates/returned stories, stale-run count, invalidation lag, and rule errors. Logs carry opaque user/story IDs, never message text.
- Run heavy historical backfills through a bounded worker queue. Each job has `(user_id, month, generation_version)` idempotency; worker retries must not publish duplicate snapshots.

## Data-quality and migration notes

`expense_daily_aggregate.user_id` is currently `VARCHAR(50)` while `app_user.id` and `financial_transaction.user_id` are `BIGINT`. Do not use this aggregate as an input to Money Stories until it is migrated to `BIGINT` with an FK and validated against transactions. Its category-level grain also cannot produce merchant or individual-evidence stories.

The existing `financial_transaction.occurred_at` is a `DATE`. That is safe for this MVP's user-local calendar-day calculations, but insufficient for time-of-day stories or cross-timezone source ingestion. Future source adapters should store an instant plus original/source timezone and derive an effective user-local date during fact mapping.

## Delivery sequence

1. Add the story schema, enum/domain DTOs, `StoryFactProvider` SPI, expense provider, rule registry, renderer, snapshot service, and read endpoint behind `MONEY_STORIES` feature flag.
2. Ship one rule (`DISCRETIONARY_FREQUENCY`) with fixture-driven rule, rendering, authorization, timezone-boundary, edit/delete invalidation, and idempotency tests. Verify exact frontend JSON against the supplied contract.
3. Add the other four rules after product signs off threshold values and copy templates. Store a new `rule_version`/`template_version` for every material rule or wording change.
5. Introduce the generic ledger/investment provider when that domain has a real persisted model. Add new investment stories as new rules; do not modify expense-story rules to understand investment table details.

## Non-goals

- AI-generated copy, arbitrary frontend styling, or client-side calculations.
- Cross-user benchmarking or external financial advice.
- Treating Money Stories as an accounting ledger or using it to compute account balances.
- A generic JSON-only financial schema before the future domain requirements are known.
