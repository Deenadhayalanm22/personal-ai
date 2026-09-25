# Backend and database engineering review

Review date: 25 September 2026. Baseline: `e9a35d6` plus the current working-tree changes, including V33 and the ongoing recurring-commitment changes. This is a review of the working tree, not just committed main.

## Assessment

**I would not approve this backend for a 1,000-concurrent-user launch in its present form.** There are correctness and recovery defects that can occur with two overlapping requests, independently of overall traffic. The largest capacity risks are external calls inside request/transaction boundaries, repeated projection work, globally scoped aggregate rebuilds, and background scheduling that scales with all historical user-months.

This does not establish a measured breaking point. One thousand registered users, one thousand connected browsers, and one thousand simultaneous expensive requests are very different workloads. No production database, deployment configuration, query plans, or load measurements were available in this review.

The application is a reasonable foundation for a modular monolith. PostgreSQL, Java 21, and Spring can support this product; replacing the stack or introducing microservices is not the immediate requirement.

### Existing strengths worth preserving

- Draft source/message uniqueness and one transaction per draft protect core duplicate expense recording.
- Confirmation and magic-link consumption already use pessimistic row locks. They should not be reported as completely unprotected read/modify/write flows.
- Owner-scoped expense/reference lookups and explicit reference-type checks protect many browser mutation paths.
- Expense lists have a bounded page size and ID cursor; calendar totals are calculated in SQL.
- Flyway migrations, many foreign keys, partial unique indexes, monetary decimals, and real PostgreSQL integration-test infrastructure exist.
- Expense-story snapshots and evidence already distinguish historical derived data from live source records.
- CI runs unit and integration suites; AI latency/token instrumentation and operational review SQL already exist.

## Severity and evidence

**Critical:** close an exposed trust-boundary hole before public traffic. **High:** data correctness, unrecoverable work, or major availability/capacity problem. **Medium:** bounded correctness gaps, growing latency, maintainability, or operational risks.

The findings below are established from source inspection. Example interleavings explain how races arise; they are not claims that a concurrent PostgreSQL reproducer was executed. Deployment-dependent risks are explicitly qualified.

## Findings

### 1. Critical — WhatsApp POST requests are not authenticated by the application

Evidence: [WhatsAppWebhookController.java:52](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/controller/WhatsAppWebhookController.java:52), [WhatsAppWebhookLoggingFilter.java:24](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/config/WhatsAppWebhookLoggingFilter.java:24), [WhatsAppAggregateBackfillCommandHandler.java:50](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WhatsAppAggregateBackfillCommandHandler.java:50).

The GET verification token protects webhook registration, but POST ingestion accepts a body without verifying its signature. The sender ID used for draft ownership, confirmation ownership, and admin-command authorization comes from that body. No application signature-verification filter was found.

If the application endpoint is publicly reachable without an upstream verifier, callers can impersonate a WhatsApp sender, inject drafts, submit guessed confirmation IDs, trigger outbound messages/model costs, or invoke aggregate commands with a known admin identity. The baseline migration also seeds a specific enabled super-admin identity.

**Recommendation:** verify the provider signature against the exact raw request bytes before parsing or executing any command; reject missing/invalid signatures; verify the expected destination/business identity. Remove deployment-specific admin identities from general migrations and provision them explicitly. Confirm any gateway protection rather than assuming it exists.

### 2. High — A committed draft can permanently suppress retry after processing fails

Evidence: [TransactionDraftWriter.java:20](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/TransactionDraftWriter.java:20), [ExpenseNormalizationHandler.java:45](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/normalization/ExpenseNormalizationHandler.java:45), [WhatsAppAudioReviewHandler.java:31](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/whatsapp/WhatsAppAudioReviewHandler.java:31).

The draft commits before normalization. Only `created=true` reaches text extraction or audio staging. If the model fails or the process stops after that commit, retrying the same provider message finds the existing draft, reports `created=false`, and skips the unfinished work. Text failures can therefore return an error initially and then return success on redelivery without ever producing an extraction. Audio failures ask for a new message but do not resume the original draft.

**Recommendation:** separate receipt deduplication from processing completion. Persist a processing state, attempt count, lease/claim, next retry time, and last error. Existing unfinished messages should resume from their persisted stage. Add crash/restart tests after each commit boundary.

### 3. High — Outbound messages have no durable delivery/retry mechanism

Evidence: [WhatsAppReplySender.java:112](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WhatsAppReplySender.java:112), [ExpenseNormalizationHandler.java:85](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/normalization/ExpenseNormalizationHandler.java:85), [ExpenseConfirmationCommandHandler.java:36](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/ExpenseConfirmationCommandHandler.java:36).

Reply sending catches exceptions and logs them. A committed extraction can remain active without its confirmation buttons ever arriving. A confirmed expense can commit without its acknowledgement arriving. A later webhook retry skips the existing draft or returns null for an already-used extraction, so it does not repair the missing notification. Process termination in the commit-to-send gap has the same result.

**Recommendation:** write an outbox event in the transaction that creates the extraction or expense; deliver it using a retrying worker with attempt/delivery state and deduplication keys. External message delivery may still be at-least-once; do not promise exactly-once delivery unless the provider supports it.

### 4. High — Webhook acknowledgement waits for a chain of external calls

Evidence: [WhatsAppWebhookController.java:56](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/controller/WhatsAppWebhookController.java:56), [WhatsAppIngestionOrchestrator.java:45](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/orchestration/WhatsAppIngestionOrchestrator.java:45), [AiExpenseNormalizationAdapter.java:106](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/normalization/AiExpenseNormalizationAdapter.java:106).

Ingestion is synchronous. Audio requires metadata lookup, download, transcription and reply. Text can require an extraction call plus a second account-recovery call, then a reply. A webhook containing multiple messages processes them sequentially. A slow dependency occupies a servlet request thread and delays acknowledgement; retries add work at exactly the wrong time.

`whatsappExecutor` exists, but the sender has no `@Async` use and ingestion does not submit work to it. Its configured pool is not protecting this flow.

**Recommendation:** authenticate and durably accept each inbound event, then acknowledge promptly. Process it with bounded worker concurrency and per-user ordering where conversation context requires it. Specify overload behavior and retry budgets. This changes the synchronous contract and must update FIN-EPIC-001 and the platform contract.

### 5. High — The shared HTTP client has no application-defined timeouts or dependency isolation

Evidence: [HttpClientConfig.java:12](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/config/HttpClientConfig.java:12), [LLMConfig.java:13](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/config/LLMConfig.java:13).

The `RestTemplate` is created without explicit connection/read deadlines. The model client is also built without application-specific timeout/retry limits. Actual transport/SDK defaults must be verified, but there is no endpoint latency budget in this code. Market lookup, transcription, and messaging share the same basic client strategy. Catching exceptions is not a timeout: the catch executes only after the call returns or fails.

**Recommendation:** set finite connect/read/overall deadlines; bounded connection and execution limits per provider; bounded, jittered retries only where safe; circuit breaking and fallback; and a maximum total request/worker budget. Measure provider latency and pool wait separately. More application threads alone will move the bottleneck to providers or the database.

### 6. High — Each monthly story read can make an AI call inside a database transaction

Evidence: [MonthlyFinancialTransactionService.java:56](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialTransactionService.java:56), [MonthlyCommitmentStoryService.java:65](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyCommitmentStoryService.java:65), [MonthlyCommitmentStoryService.java:118](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyCommitmentStoryService.java:118), [AiMoneyStoryCopyGenerator.java:30](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/AiMoneyStoryCopyGenerator.java:30).

With an API key configured, every live monthly-commitment read generates fresh copy, even when facts are unchanged. This request path is transactional and performs database reads before model generation. Background pattern-story rendering can also invoke the model inside snapshot publication's repeatable-read transaction.

Consequences include request latency, repeated cost, occupied database connections, and long transaction/snapshot lifetimes during unrelated provider delays. The comment that web reads do not generate stories does not describe the live commitment exception accurately.

**Recommendation:** persist/cache rendered copy by facts fingerprint, locale, prompt and model version; serve deterministic fallback immediately; generate wording outside database transactions. Publish only if the source revision still matches. Keep source facts deterministic and current.

### 7. High — Nested `REQUIRES_NEW` reads create a connection-pool starvation path

Evidence: [MonthlyFinancialSnapshotService.java:67](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialSnapshotService.java:67), [MonthlyFinancialSnapshotService.java:73](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialSnapshotService.java:73), [MonthlyFinancialTransactionService.java:56](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialTransactionService.java:56).

The outer story transaction calls snapshot methods using `REQUIRES_NEW`. When the outer transaction has acquired a connection, the inner operation needs a second one while the first remains allocated. A burst of outer transactions can occupy the whole pool and then all wait for inner connections. Exact acquisition behavior depends on transaction-manager/driver settings, so measure it, but the hazardous propagation arrangement is present.

**Recommendation:** remove the enclosing transaction from orchestration and establish short, deliberate database boundaries; avoid using independent transactions for ordinary projection reads. If lazy creation requires a write, coordinate that separately. Pool sizing is a secondary mitigation, not a substitute for fixing this call graph. See [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).

### 8. High — Concurrent source writes can publish an incomplete monthly snapshot

Evidence: [MonthlyFinancialSnapshotService.java:91](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialSnapshotService.java:91), [MonthlyFinancialSnapshotRepository.java:11](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/repository/MonthlyFinancialSnapshotRepository.java:11), [WebLoanService.java:48](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoanService.java:48), [WebRecurringCommitmentService.java:54](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebRecurringCommitmentService.java:54).

Every relevant write rebuilds current and next snapshots, but there is no shared user/projection lock or revision protocol. Example: two requests add different commitments for the same user; each calculates a payload before seeing the other's uncommitted change; both source rows eventually commit, but the last snapshot payload can include only one change. Atomicity within each transaction does not serialize the two transactions. Concurrent first creation can instead hit the unique user/month constraint and fail a request.

The persisted `source_fingerprint` is a hash of the output payload, not a concurrency check against a source revision. Existing snapshots are accepted by calculation version without verifying freshness against source state.

**Recommendation:** initially serialize relevant writes per user using a short database lock acquired before modifying sources or building the projection. Batch source reads. A later revision/compare-and-swap projection worker is an alternative, but asynchronous freshness would change the current documented immediate-refresh guarantee.

### 9. High — Most financial edits and outcome transitions have no concurrency control

Evidence: [FinancialTransactionEditService.java:45](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/FinancialTransactionEditService.java:45), [FinancialTransactionEntity.java:28](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/entity/FinancialTransactionEntity.java:28), [WebStockService.java:104](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebStockService.java:104), [WebLoanService.java:127](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoanService.java:127), [WebRecurringCommitmentService.java:92](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebRecurringCommitmentService.java:92).

There are no `@Version` fields in the reviewed entity package. The expense edit lookup and most planning outcome lookups are not locking reads. Two requests can both validate the same old state and save incompatible results. Examples: amount/category edits overwriting each other, confirm versus skip both succeeding, two loan skips losing a tenure extension, or payment versus restructure observing stale history. A transaction annotation does not prevent these schedules.

**Recommendation:** optimistic versions and expected-version/ETag contracts for normal edits; parent-row locks or conditional updates for outcome transitions and cross-row invariants; deterministic lock order. Map conflicts to a documented 409 and add concurrent PostgreSQL tests. Preserve the existing confirmation and magic-link locks.

### 10. High — Extra-charge totals can disagree with their detail rows

Evidence: [WebRecurringCommitmentService.java:141](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebRecurringCommitmentService.java:141), [V22__add_commitment_extra_amount.sql:3](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/db/migration/V22__add_commitment_extra_amount.sql:3).

The service reads `extraAmount`, adds the request amount in Java, writes the occurrence, then inserts a detail row. Two calls starting from total 100 and adding 20 and 30 can leave a stored total of 120 or 130 while the detail rows sum to 150. Locking during the eventual UPDATE does not make the earlier Java calculation current.

**Recommendation:** make the detail rows authoritative and derive their sum, or update the total atomically under a shared occurrence lock. Add an idempotency key so retrying an accepted extra does not create another charge. Apply the same lock/atomic-update discipline to `CommitmentSavingsService.allocate`, which uses a read/check/increment on `usedAmount`.

### 11. High — Dirty-date processing can lose a concurrent invalidation

Evidence: [ExpenseDailyAggregateRepository.java:16](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/repository/ExpenseDailyAggregateRepository.java:16), [ExpenseDailyAggregationService.java:19](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/ExpenseDailyAggregationService.java:19).

Possible schedule: a dirty row already exists; a worker locks it; a writer changes a transaction and marks the same date with `ON CONFLICT DO NOTHING`; the writer has not committed when the worker aggregates; the worker sees old facts and deletes the marker; the writer commits afterward. The writer's no-op marker insert did not create a new generation of work. The derived totals may stay stale, and story evidence validation can repeatedly reject publication.

**Recommendation:** use a generation/revision-aware dirty record, or an upsert that updates a marker under a consistent locking protocol, then acknowledge only the processed revision. Key work by `(user_id, aggregate_date)`. Validate the exact SQL protocol with a two-connection PostgreSQL test; a JVM lock cannot cover multiple instances.

### 12. High — Aggregate rebuilds span all users and multiple callers are not coordinated

Evidence: [ExpenseDailyAggregationService.java:27](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/ExpenseDailyAggregationService.java:27), [ExpenseDailyAggregateRepository.java:48](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/repository/ExpenseDailyAggregateRepository.java:48), [FirstWhatsAppMessageAggregationTrigger.java:38](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/FirstWhatsAppMessageAggregationTrigger.java:38), [ExpenseDailyAggregationScheduler.java:36](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/ExpenseDailyAggregationScheduler.java:36).

One user's dirty date triggers deletion and rebuilding of everyone's data for that date. First-message work, cron, and manual backfill can all call rebuild paths. Only the dirty-queue path takes dirty-row locks; direct callers bypass them. The first-message `synchronized` guard is local to one JVM and resets on restart. Two instances can rebuild the same date, duplicate work, contend, or hit aggregate uniqueness conflicts.

`rebuildMissingBefore` also processes every missing date in one transaction, making recovery duration and lock/WAL volume proportional to the backlog.

**Recommendation:** one coordinated job mechanism with per-user/date claims; bounded transactions per unit; shared lease/advisory-lock protocol across every entry point; crash recovery. Ensure an empty aggregate still acknowledges a successfully rebuilt user/date so deletion is represented correctly.

### 13. High — Story scheduling has a deterministic backlog/starvation risk

Evidence: [MoneyStoryGenerationScheduler.java:26](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MoneyStoryGenerationScheduler.java:26), [MoneyStoryAggregateRepository.java:41](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/repository/MoneyStoryAggregateRepository.java:41), [MoneyStoryAggregateRepository.java:69](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/repository/MoneyStoryAggregateRepository.java:69).

Default selection is 100 user-months every 15 minutes, processed sequentially. All historical months become eligible again when `evaluated_on` differs from today. Selection orders oldest month first. A single healthy worker has a ceiling of 9,600 attempted user-months/day before generation time and failures; 1,000 users with 12 active historical months already exceed that recurring daily selection budget. Newer months can starve as older months become eligible again the next day. A repeatedly failing early batch also monopolizes selection because failures have no persisted backoff.

The selection query computes distinct months from the transaction table and runs correlated change checks; its LIMIT does not bound all preceding work. Multiple app replicas independently choose the same work; no claim protects story generation. Unique current-snapshot constraints prevent multiple current rows but do not avoid duplicate compute or serialization failures.

**Recommendation:** durable dirty user/month jobs, current-month freshness ticks only where time changes semantics, claims/leases, retry backoff and failure isolation, bounded worker concurrency, and backlog-age metrics. Historical months should rerun when relevant data/rules change, not merely because the date advanced.

### 14. High — Reference merges leave downstream ownership links and projections inconsistent

Evidence: [WebReferenceMergeService.java:26](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebReferenceMergeService.java:26), [MonthlyFinancialSnapshotService.java:177](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialSnapshotService.java:177), [V18__add_credit_card_profiles.sql:4](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/db/migration/V18__add_credit_card_profiles.sql:4), [V16__add_user_recurring_commitments.sql:12](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/db/migration/V16__add_user_recurring_commitments.sql:12).

Merge repoints financial transactions and aliases and deactivates source references. It does not repoint credit-card profiles or commitment merchant links, mark affected aggregate dates dirty, or rebuild monthly projections. After merging an account used by a card into another account, the card can retain the old reference while spending now belongs to the new reference. Its next bill calculation can omit that spending. Merchant aggregate rows retain old reference IDs and can disagree with current transaction evidence.

**Recommendation:** define a complete reference-merge dependency graph. Repoint dependent domain records and invalidate all affected projections atomically. If both accounts have card profiles, resolve that explicit conflict rather than silently choosing one. Add merchant-story and card-bill regression tests. Avoid loading every historical transaction into application memory for large merges.

### 15. Medium — Inactive merged reference identities can be reused

Evidence: [ConfirmedReferenceWriter.java:33](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/ConfirmedReferenceWriter.java:33), [WebUserReferencePreferenceService.java:82](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebUserReferencePreferenceService.java:82).

Confirmation finds a canonical name without requiring active status; preference creation explicitly reactivates an inactive reference. After a merge, an old name can therefore reconnect new expenses to the redundant row or split the identity again. Prompting the model with preferred aliases does not establish a database invariant.

**Recommendation:** retain a `merged_into_id` redirect and resolve names/aliases through the current canonical identity. Explicitly define whether users may undo merges. Treat ambiguous aliases as ambiguous instead of allowing model choice to become the identity rule.

### 16. Medium — Several find-then-insert flows treat uniqueness as error handling

Evidence: [AppUserService.java:19](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/AppUserService.java:19), [PendingActionContextService.java:75](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/PendingActionContextService.java:75), [WebMutualFundService.java:263](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebMutualFundService.java:263), [ActionManagementService.java:38](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/ActionManagementService.java:38).

Uniqueness is good, but check-then-insert is not a complete concurrency strategy. Two first contexts have no existing row to lock. Two list requests can create the same missing SIP occurrence. Two reminder workers can both pass the existence check. The losing request/batch can fail instead of becoming idempotent.

`AppUserService` catches a flush constraint failure and queries again. In `TransactionDraftWriter` it joins an existing transaction; after a PostgreSQL statement error and transactional repository failure, continuing in that transaction is not a reliable recovery path.

**Recommendation:** atomic `INSERT ... ON CONFLICT` where semantics allow, parent locks for absent-child creation, or retry the entire transaction from outside the failed transaction. Translate expected conflicts consistently. Do not assume catching the exception restores transaction usability.

### 17. High — Portfolio responses serialize network fan-out while holding database work open

Evidence: [WebStockService.java:169](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebStockService.java:169), [WebMutualFundService.java:317](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebMutualFundService.java:317), [MfApiService.java:23](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MfApiService.java:23), [YahooFinanceStockMarketDataAdapter.java:27](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/YahooFinanceStockMarketDataAdapter.java:27).

Each holding loads its full transaction history and requests a fresh remote price/NAV. Calls are sequential within a list. Mutual-fund search makes another NAV request for every returned search result, with no local result limit. Stock search also adds per-result price lookups. Portfolio create/update responses call these summary paths before their transactions finish.

Ten holdings with a hypothetical 300 ms quote latency add roughly three seconds of network time alone. The same instruments are fetched repeatedly across users, amplifying provider throttling and cost.

**Recommendation:** return committed domain data independently of live valuation; cache quotes by instrument with freshness timestamps; refresh/batch outside transactions; cap search results before enrichment; query holding sums in SQL and paginate history. Bound any parallel provider calls rather than submitting one task per holding without limits.

### 18. Medium — High query amplification in snapshot, commitment, and reference reads

Evidence: [MonthlyFinancialSnapshotService.java:91](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialSnapshotService.java:91), [WebRecurringCommitmentService.java:230](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebRecurringCommitmentService.java:230), [WebUserReferencePreferenceService.java:32](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebUserReferencePreferenceService.java:32), [AiExpenseNormalizationAdapter.java:237](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/normalization/AiExpenseNormalizationAdapter.java:237).

Snapshots query occurrences per loan, per commitment date, and savings entries per plan; every source write repeats work for two months. Daily commitments can add one lookup per due date. Commitment review fetches all commitments again for each candidate. Reference listing queries aliases and counts per reference; AI prompt preparation also queries aliases per reference and includes the entire reference set.

**Recommendation:** bulk fetch by owner and bounded date range, group by ID in memory, use grouped SQL totals, and retrieve aliases/counts in batches. Reuse one set of source facts across current/next projection calculations. Set query-count budgets for common page loads and representative heavy users. Bound the number of reference candidates sent to the model.

### 19. Medium — GET portfolio requests mutate financial occurrence state

Evidence: [WebStockService.java:71](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebStockService.java:71), [WebMutualFundService.java:188](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebMutualFundService.java:188).

A list request inserts/promotes SIP occurrences. Multiple tabs, automatic refresh, and retries can collide on creation, and read load becomes write load. A user not opening the stock list also follows a different occurrence-materialization path from an active browser user.

**Recommendation:** materialize due occurrences in an idempotent worker or a deliberate command; project virtual due state for reads if appropriate. Keep GET side effects out of financial lifecycle decisions. Update the existing documented list behavior when changing this contract.

### 20. High — Input limits allow excessive CPU, memory, database and provider work

Evidence: [WebLoanService.java:214](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoanService.java:214), [WebLoanService.java:178](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoanService.java:178), [RecurringCommitmentSchedule.java:36](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/RecurringCommitmentSchedule.java:36), [WhatsAppAudioTranscriber.java:55](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/whatsapp/WhatsAppAudioTranscriber.java:55).

Loan tenure has a positive-only bound and is used to generate a full schedule. An enormous accepted value can allocate a huge list or trigger date overflow. Recurrence walks from the anchor to the requested month one interval at a time; distant dates make this proportional to the distance. Several linked-ID and text inputs lack bounded sizes. Audio checks the actual size only after `byte[]` download; metadata helps but does not bound the read itself. Many simultaneous valid large audio downloads can also exhaust heap.

**Recommendation:** business-appropriate upper bounds on tenure/date horizons, text lengths, IDs per request and search results; arithmetic recurrence alignment instead of day-by-day catch-up; streamed media with a hard byte ceiling and a small audio concurrency limit. Reject invalid work before external calls and allocation.

### 21. Medium — Monetary validation occurs before rounding

Evidence: [FinancialTransactionEditService.java:64](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/FinancialTransactionEditService.java:64), [WebLoanService.java:236](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoanService.java:236), [WebMutualFundService.java:363](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebMutualFundService.java:363), [WebIncomeService.java:53](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebIncomeService.java:53).

For example, `0.001` passes the positive check and rounds to `0.00`. Tables with positive CHECK constraints reject it later, often as an unmapped server error. Tables without corresponding checks can store a zero amount. Tiny units/NAV can similarly round to zero and then participate in division. Amount/unit/price consistency is not validated when all are supplied.

**Recommendation:** normalize scale first, validate the normalized value and representable maximum, and document rounding/tolerance rules. Use explicit 4xx responses for invalid financial input. Do not require exact amount=units×price equality without a defined rounding/fee policy.

### 22. Medium — Backend validation and error translation are incomplete

Evidence: [WebIncomeService.java:34](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebIncomeService.java:34), [WebRecurringCommitmentService.java:119](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebRecurringCommitmentService.java:119), [WebApiExceptionHandler.java:10](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/exception/WebApiExceptionHandler.java:10).

Immutable `Set.of(...).contains(null)` calls can throw on missing salary visibility/frequency/range. Commitment path strings are sliced before validating length/date format. Reference preference strings are not checked against schema maximum lengths. The exception advice does not normalize JSON-body parse failures, constraint conflicts, optimistic conflicts, or these uncaught argument errors into the promised application envelope.

**Recommendation:** boundary DTO validation plus domain validation; parse before slicing; consistent error codes; targeted handling of known constraint and concurrency failures. Avoid mapping every unexpected database failure to a client error.

### 23. Medium — Portal access revocation does not invalidate existing access

Evidence: [UserAccessService.java:44](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/UserAccessService.java:44), [WebAuthenticationService.java:45](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebAuthenticationService.java:45), [WebAuthenticationService.java:107](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebAuthenticationService.java:107).

Disabling `portal_enabled` affects issuance through the login request service, but existing sessions and previously issued unused links do not recheck that flag. Revoking access can therefore leave portal access alive until session/link expiry. Demo mode should still be authorized through the authenticating owner, not by checking the demo user's default flag.

**Recommendation:** explicitly define revocation semantics; if disable means access removal, revoke active links/sessions or validate an owner authorization epoch on every request. Add real-profile and demo-profile revocation tests.

### 24. Medium — Login throttling is local, permanently retains keys, and is proxy-sensitive

Evidence: [WebLoginRequestService.java:28](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoginRequestService.java:28), [WebLoginRequestService.java:59](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoginRequestService.java:59), [WebFinanceController.java:34](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/controller/WebFinanceController.java:34).

The synchronized deque makes each key safe inside one JVM. However, each instance has an independent limit and restart resets it. Keys are never removed, allowing continual unique phone/IP inputs to grow the map. IP limits can unfairly throttle users sharing an address; behind a proxy, the chosen address must be checked against the deployment's trusted-forwarding configuration. Unconditional trust in client forwarding headers would be another mistake.

**Recommendation:** expiring bounded shared limits, separately tuned phone and IP policies, and a validated proxy trust boundary. Persist or observe abuse signals without retaining unlimited attacker-controlled keys.

### 25. Medium — Security configuration depends too heavily on deployment assumptions

Evidence: [CorsConfig.java:29](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/config/CorsConfig.java:29), [WebFinanceController.java:415](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/controller/WebFinanceController.java:415), [application.yaml:27](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/application.yaml:27), [E2eSupportController.java:19](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/e2e/E2eSupportController.java:19).

Cookies are configurable for Secure/SameSite; no explicit CSRF token or unsafe-method Origin validation was found. Exploitability depends on origin topology, SameSite policy, accepted content types and browser behavior; this is a hardening gap, not a proven cross-site exploit. CORS alone is not a complete write-authorization policy. Actuator metrics are exposed with no application security rule found; gateway restrictions were not inspected. E2E session/clock controls ship in main sources and are profile gated.

**Recommendation:** central authentication/authorization, explicit browser write protection appropriate to the deployment, restricted operational endpoints, and fail-closed production profile checks. Keep test fixture endpoints out of the production artifact where practical. Verify the actual runtime environment rather than changing settings blindly.

### 26. Medium — Time and calendar semantics are inconsistent

Evidence: [ExpenseNormalizationHandler.java:19](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/normalization/ExpenseNormalizationHandler.java:19), [WebMutualFundService.java:229](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebMutualFundService.java:229), [WebRecurringCommitmentService.java:188](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebRecurringCommitmentService.java:188), [V1__init.sql:10](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/db/migration/V1__init.sql:10).

Capture defaults to Asia/Kolkata, investments often use the injected clock's zone, commitment defaults sometimes use the system clock, and other operations use the user's zone. Around a month boundary these can disagree about current/due month. Schema timestamp types also mix TIMESTAMP and TIMESTAMPTZ for event instants.

**Recommendation:** one injectable Clock and explicit user/business timezone per calendar decision; `DATE` for date-only obligations; `TIMESTAMPTZ` for instants. Convert existing timestamp data using its known historical timezone, not an implicit cast. Add midnight/month-end tests in at least two zones.

### 27. Medium — Loan and SIP plan revisions are overwritten rather than effective-dated

Evidence: [WebLoanService.java:151](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebLoanService.java:151), [MonthlyFinancialSnapshotService.java:144](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialSnapshotService.java:144), [WebMutualFundService.java:82](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebMutualFundService.java:82), [V27__loan_schedule_events.sql:1](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/db/migration/V27__loan_schedule_events.sql:1).

A loan restructure immediately overwrites its single EMI amount and tenure, even for a future effective month. Monthly snapshot construction uses that amount without selecting a revision by effective date. The future amount can consequently influence the current month's projection. Occurrence snapshots preserve some completed history, but do not represent the full schedule revision. SIP frequency/anchor revisions likewise live in one mutable row.

**Recommendation:** represent plan revisions with effective dates and select the applicable revision for each due date; retain original occurrence facts. This is targeted schedule history, not a proposal to turn the expense tracker into a full accounting ledger. Include future-effective and repeated-restructure tests.

### 28. Medium — AI wording is trusted more than the comments imply

Evidence: [AiMoneyStoryCopyGenerator.java:48](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/AiMoneyStoryCopyGenerator.java:48), [MonthlyCommitmentStoryService.java:118](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyCommitmentStoryService.java:118), [BaseLLMExtractor.java:36](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/llm/BaseLLMExtractor.java:36).

The copy validator checks presence/enums and one heading length. It does not verify that generated amounts, dates, claims or other word limits match supplied facts. Model instructions cannot guarantee the no-invented-facts acceptance criterion. Parsing failures also embed raw model output in exceptions, which may reach logs with private financial text.

**Recommendation:** keep all factual clauses deterministic or render them through verified placeholders; limit generated wording to non-factual connective text; validate lengths and fall back safely. Redact sensitive model output in exception logging and retain only controlled diagnostic samples.

### 29. Medium — Retention and operational failure handling are incomplete

Evidence: [V1__init.sql:32](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/db/migration/V1__init.sql:32), [V5__add_money_stories.sql:67](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/db/migration/V5__add_money_stories.sql:67), [MoneyStoryGenerationScheduler.java:40](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MoneyStoryGenerationScheduler.java:40), [application.yaml:31](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/resources/application.yaml:31).

No scheduled cleanup was found for expired sessions/links/contexts, abandoned drafts, or superseded story/evidence history. Over time, raw messages and repeated evidence snapshots grow storage, indexes and backups. The story worker counts attempts as generated even after a caught failure, making its success log misleading. Existing AI metrics are useful but do not cover queue age, outbox delivery or projection revision lag because those lifecycle records do not exist yet.

**Recommendation:** explicit retention by data class; chunked expiry cleanup with supporting indexes; retention of required financial history; success/failure counters separated from attempts; alerts on oldest pending work, pool wait, deadlocks, query latency and repeated publication errors. Check backup restoration and actual production resource limits separately.

### 30. Medium — Architecture and tests make cross-domain correctness fragile

Evidence: [WebManager.java:17](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/WebManager.java:17), [WebFinanceController.java:16](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/controller/WebFinanceController.java:16), [MonthlyFinancialSnapshotService.java:47](/Users/deena/Documents/GitHub/personal-ai/backend/src/main/java/com/apps/deen_sa/service/MonthlyFinancialSnapshotService.java:47), [ci.yml:1](/Users/deena/Documents/GitHub/personal-ai/.github/workflows/ci.yml:1).

The web facade/controller span almost all domains. Numerous services carry compatibility constructors, optional dependencies and null branches specifically for old tests. Core writes must remember every projection refresh manually; the merge omissions demonstrate the resulting risk. Dense one-line financial methods obscure transaction boundaries and make review harder. Tests built without required collaborators can pass while omitting production side effects.

**Recommendation:** domain-focused modules/controllers; required constructor dependencies; explicit command services and invariant-owning methods; small projection interfaces; test fixtures that supply real contracts or deliberate mocks. Keep CI's integration stage and add focused concurrency/recovery tests. A rewrite is unnecessary; migrate one flow at a time.

## Database structure and normalization

The schema has useful relational structure. Denormalized snapshots and historical evidence are legitimate read models. The priority is making their ownership, revision and rebuild rules reliable, not decomposing every JSON field into tables.

| Area | Problem / nuance | Suggested change |
|---|---|---|
| Tenant integrity | A foreign key to a merchant/account/commitment proves existence, not that it belongs to the transaction's user or has the correct type. Current service checks cover many entry points but the database cannot enforce that invariant. | Consider composite owner FKs using unique `(user_id, id)` parent keys; typed reference tables or constrained type discriminators for role-sensitive relationships. RLS is optional defense in depth and requires careful connection-context handling. |
| Taxonomy | Category/subcategory labels and derived spending nature repeat in drafts, transactions and aggregates; no relational check enforces a valid pair. Renaming can require data migrations. | Stable classification IDs with a category/subcategory mapping; decide whether historical labels/nature are versioned snapshots or follow current taxonomy. Preserve that decision in migration/tests. |
| Reference aliases | Uniqueness is per reference, so the same user's/type's alias can point to multiple identities. | Define ambiguity policy; if identity aliases must be unique, store an owner/type-scoped normalized alias key. Add canonical redirects for merges. |
| Extra amounts | Occurrence total duplicates the sum of detail rows without protected maintenance. | Derive it or maintain it atomically; finding 10. |
| Recurring links | The estimate-source join table and `financial_transaction.recurring_commitment_id` look similar but serve different purposes: estimate evidence versus actual matching. | Keep distinct semantics if both are needed; rename/document them and define correction/deletion behavior. Do not collapse them solely because both relate transactions to commitments. |
| Recurring occurrences | `scheduled_month` now stores exact dates for day/week commitments as well as first-of-month keys. | Rename to a clear occurrence/scheduled-date key and define uniqueness per schedule type. Monthly-only tables should enforce month-start values. |
| Schedule revisions | Loan and SIP effective changes overwrite the parent configuration. | Add effective-dated revisions and make occurrence facts immutable once recorded, with explicit correction semantics. |
| Monetary checks | `expense_daily_aggregate` uses NUMERIC(15,2), smaller than source NUMERIC(19,2); valid source values/sums can overflow. Several later savings/occurrence/extra tables lack positive/nonnegative checks. | Align precision with bounded source/sum requirements; add status-dependent amount/date checks after validating legacy rows. |
| Status checks | Loan/recurring occurrences and savings entries store unconstrained strings; investment SIP status/frequency and recurring modes/units lack some DB checks. | Add enumerated CHECKs and required-field combinations, e.g. PAID requires payment fields, SAVED requires positive amount. Application enums alone do not protect SQL writers. |
| Income consistency | Allowed values exist, but visibility does not constrain which salary fields must be null/present. | Enforce RANGE/EXACT/SKIPPED field combinations. |
| Payloads | JSON snapshots stored as TEXT cannot be validated as JSON by the column type. That alone does not cause poor performance. | JSONB if structured DB queries/validation are useful; otherwise versioned serialized documents are acceptable. Do not add GIN indexes without a query need. |
| Action references | Polymorphic `reference_type/reference_id` has no FK; deletion integrity is manual. | For the current loan-only use, a real loan FK is simpler; preserve polymorphism only if justified and give it lifecycle ownership. |
| Financial history | Expenses are intentionally mutable with soft delete; loan/investment deletes remove associated history. `updated_at` cannot reconstruct prior values. | If support/audit requirements demand history, add append-only change records with actor/reason/version. This is a product-scope decision; a full double-entry ledger is outside the current documented feature scope. |
| Instrument identity | Provider/symbol identity and user position are combined; quote requests repeat across owners. | A shared instrument/quote model or cache can separate provider identity from user holdings without copying an entire external catalogue. |

### Index review: candidates to verify with real query plans

These are proposals, not migrations to apply blindly. Existing indexes already cover many user/date and parent/occurrence lookups through unique constraints.

- **Global date rebuilds:** `financial_transaction(occurred_at, user_id) WHERE deleted_at IS NULL` if global rebuilds remain. The existing `(user_id, occurred_at)` index is optimized for a different leading key. Prefer removing global work first.
- **Card statement totals:** `(user_id, source_account_id, occurred_at) WHERE deleted_at IS NULL`, potentially including amount after evaluating size/write costs.
- **Reference aggregate deletion:** a date-leading index on `expense_daily_reference_aggregate` if date-wide deletion remains; its current keys start with user.
- **Commitment unlink:** index `financial_transaction(recurring_commitment_id)` for `findByRecurringCommitmentId`; add reverse lookup indexes for join-table/FK maintenance only where needed.
- **Extra detail loading:** `(occurrence_id, created_at)` on `recurring_commitment_extra`; no supporting index was present in V22.
- **Expiry cleanup:** expiry indexes on session/link/context data if chunked purging is implemented.
- **Expense cursor:** evaluate date selectivity versus ID ordering; `(user_id, id DESC)` partial index may help some shapes, while the existing user/date index may be better for narrow months. Measure both heavy-user history and small-month cases.
- **Worker selection:** replace global historical-month discovery with an indexed queue rather than accumulating expression indexes around a fundamentally growing scan.
- **Redundant indexes:** extraction's separate draft index duplicates its unique draft index. Snapshot's separate user/month index overlaps the unique user/month key; verify ordering requirements before removing it. Foreign keys do not automatically imply an index on their referencing columns.

Use `EXPLAIN (ANALYZE, BUFFERS)` on representative non-production data, record row estimates versus actuals, and measure write cost as well as read latency. Account for soft-deleted rows, skewed users, many aliases and long histories.

## What 1,000 concurrent users would mean here

Capacity must be modeled per operation:

| Workload | Likely first constraint | What to measure |
|---|---|---|
| Idle authenticated browsers | Little backend work unless the frontend polls | Requests per active user and reconnect bursts |
| Dashboard refresh burst | Model latency, DB pool occupancy, nested transactions | p95/p99 endpoint time, pool pending/acquire time, model calls per refresh |
| WhatsApp text burst | Synchronous external latency and request threads | ACK latency, completed captures/sec, retry amplification |
| Audio burst | Heap and media/transcription concurrency | Peak bytes in flight, GC pauses, worker age |
| Portfolio browsing | Serial quote fan-out and repeated history reads | Provider calls/page, cache hit rate, SQL count |
| Many financial edits | Per-user/source conflicts and projection rebuild cost | Lock wait, conflicts, source/projection consistency |
| Two application replicas | Unclaimed recurring work and instance-local state | Duplicate jobs, constraint failures, backlog fairness |
| Years of history | Historical worker re-evaluation and scans | Rows scanned, user-month queue age, batch duration |

Illustrative arithmetic, not benchmarks: with 10 connections and five seconds of connection occupancy per request, that pool can complete roughly two such requests/second even before other work competes. One thousand simultaneous such requests would need about 500 seconds of idealized service time; deadlines would typically reject many earlier. If 200 request threads each wait five seconds externally, their ideal ceiling is about 40 requests/second. Use the deployed pool/thread counts and measured service times to replace these assumptions.

There is no basis here for a promise that 10 users are safe or that exactly 1,000 will fail. Several correctness findings need only two concurrent operations; large-user-history problems can occur with one user.

## Recommended architecture and rollout order

### Phase 1 — Protect correctness and recoverability

1. Authenticate inbound webhooks and remove environment-specific seeded privilege.
2. Add resumable inbox processing and a transactional outbox; test crash boundaries.
3. Protect editable financial state with versions, transition locks and idempotency keys.
4. Fix extra totals, snapshot concurrent publication, dirty-marker acknowledgement and reference-merge propagation.
5. Validate normalized money, request bounds and effective-dated schedule behavior.

### Phase 2 — Remove slow work from critical transactions

1. Acknowledge durable webhook receipt promptly and use bounded workers.
2. Generate/copy-cache AI text outside transactions; serve deterministic current facts and fallback copy.
3. Cache quote/NAV data with timestamps and bounded refresh concurrency.
4. Remove accidental nested transaction boundaries; bulk-load projection facts.
5. Replace worker discovery with claimed dirty-key jobs and per-job retries.

Preserve the current immediate monthly-snapshot guarantee with short serialized writes initially. Changing it to eventual consistency requires a conscious product/contract change with visible freshness semantics.

### Phase 3 — Prove capacity and operational readiness

1. Run concurrency integration tests against PostgreSQL, not repository mocks.
2. Load test a documented mix using deterministic provider stubs and controlled latency/failure; separately test provider integration within its limits.
3. Tune indexes and pool budgets from query plans and latency measurements.
4. Add queue/revision lag, dependency, database and error alerts; implement retention and restore drills.
5. Refactor domain boundaries incrementally after invariants are covered.

The target flow can remain one deployable modular monolith with separately scalable workers:

```text
Signed inbound event -> durable inbox -> bounded processing -> short domain transaction
                                                           -> extraction/expense + outbox
Outbox worker -> provider -> recorded delivery/retry outcome
Domain command -> locked/versioned source update -> atomic current projection or dirty revision
Read API -> bounded projection queries + cached quote/copy -> response
```

PostgreSQL-backed inbox/outbox/job tables are sufficient as an initial design. A dedicated broker becomes a choice based on measured queue requirements, not a prerequisite for 1,000 users.

## Verification plan for the fixes

| Test | Required invariant |
|---|---|
| Kill after draft commit; redeliver same message | Exactly one eventual extraction or explicit terminal failure; no silent stuck draft |
| Fail reply delivery after extraction commit; restart | Pending outbox work is delivered/retried and remains observable |
| Concurrent confirmation of same extraction | One expense; duplicate action has defined harmless response |
| Concurrent first messages for the same new user | One user; no poisoned transaction recovery |
| Concurrent expense edits / edit versus delete | Conflict or serialized behavior; no lost edit or resurrection |
| Concurrent confirm versus skip / two loan skips | Valid single transition and correct tenure |
| Two simultaneous extras | Total equals sum of details; request retry does not duplicate |
| Dirty mark during aggregate rebuild | A later rebuild remains queued or includes the committed write |
| Two source changes for same user | Final snapshots include both committed changes |
| Cold concurrent monthly snapshot reads | One valid row per month and no uncontrolled unique-constraint error |
| Merge a card account and merchant with history | Card totals, references and newly regenerated story evidence agree |
| Two worker replicas; restart mid-job | Bounded duplicate work, safe claim expiry, no permanent starvation |
| More than 9,600 historical user-months | Current-month changes still meet the chosen freshness target |
| Slow/unavailable AI and quote providers | Ordinary reads remain bounded; no DB-pool collapse |
| Very long tenure/date/IDs and oversized media | Early 4xx or bounded worker failure, stable memory |
| Future-effective restructure | Current and future months use their respective plan versions |
| Revoke owner while demo selected | Existing access follows documented revocation policy |
| Midnight and month-end in multiple zones | Consistent due dates and month selection |

Start load runs at 10, 50, 100, 250, 500 and 1,000 concurrent virtual users with specified think time and request mix. Also run an open arrival-rate test so rising latency does not silently reduce offered load. Include heavy histories and a sustained soak, not just an empty-database burst. Set acceptance thresholds before running; proposed starting targets could be sub-second p95 ordinary cached reads and sub-second durable webhook ACK, then refine to deployment/product needs. Measure correctness alongside latency and errors.

## Validation performed and limits

Read the principal ingestion, authentication, expense, reference, loan, investment, commitment, savings, snapshot and worker implementations; relevant entities/repositories; all migration scripts present through V33; active feature specifications/contracts; runtime configuration; CI and representative tests.

Executed seven existing focused test classes under Java 21 using offline Maven: ExpenseNormalizationHandlerTest, WebAuthenticationServiceTest, FinancialTransactionCalendarServiceTest, ExpenseConfirmationCommandHandlerTest, MonthlyFinancialSnapshotCreditCardTest, WebReferenceMergeServiceTest and WebLoanServiceTest. **17 tests passed, zero failures/errors/skips.** These are focused unit tests and do not prove the identified race schedules safe.

No application Java, schema or feature contract changes were made. Only this review document was added. Existing working-tree changes were left intact. No production traffic, provider calls, database migrations, live query-plan measurements or 1,000-user load test were performed. No dependency vulnerability audit was performed; version age alone is not evidence of a specific vulnerability.

Concurrency recommendations are consistent with [PostgreSQL row locking](https://www.postgresql.org/docs/17/explicit-locking.html) and [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html). These explain framework/database behavior; repository-specific findings come from the linked code.
