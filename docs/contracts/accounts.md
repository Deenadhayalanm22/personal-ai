# Unified expense accounts

Accounts provide planning context and reuse the owned `ACCOUNT` identity used by expense capture, options and corrections. Your money has one Accounts section for multiple bank/debit accounts, credit cards and captured names awaiting configuration. A directly visible income-context card retains salary range/exact/skip and masked saved-summary/edit access for planning and AI insights; it uses the existing private income-outlook API.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/web/accounts` | Returns `{currency,accounts}` for active owned references. Each account is `{id,name,type,cardId,issuerName,statementDay,dueDay,startMonth}`. |
| `POST /api/web/accounts` | Accepts `{accountReferenceId?,name?,type,issuerName?,statementDay?,dueDay?,startMonth?}`; returns the configured account. New names require 1–120 characters. Canonical names or unambiguous aliases reuse their existing identity. Explicit IDs must be active and owned. |
| `PATCH /api/web/accounts/{id}` | Same configuration shape; configures a captured name or updates card billing details without renaming or reassigning its identity. |

`type` is `BANK` or `CREDIT_CARD`; captured references with neither configuration return `UNCONFIGURED`. Bank/debit accounts require only name and type. Credit cards require issuer (1–120 characters), generation day and due day (1–28), using the [credit-card contract](credit-cards.md). Existing active cards retain their expense references and billing history.

Accounts have no opening balance, receipts, available balance, account spending summary or balance deductions. There is no money-received endpoint or payment funding-account field. Salary remains an optional planning estimate, not a recorded deposit. Card settlements only reduce the captured bill due and never create an expense or update a bank balance. Existing unused receipt/balance storage from V40 is retained for migration compatibility; it is neither read nor written by these flows.

Writes lock account references and name creation serializes per profile. Configured types cannot switch between bank and credit. Invalid input returns `400 INVALID_ACCOUNT`; foreign/inactive identities return `404 ACCOUNT_NOT_FOUND`. WEB_SESSION selects active real/demo ownership. Configured identities cannot be merged through name cleanup (`409 CONFIGURED_ACCOUNT_MERGE`); aliases remain supported. Legacy card APIs reject bank-configured references. New accounts are immediately available in expense options and capture.

Coverage: `WebAccountsPostgresTest` checks simple setup, identity reuse, ownership, card setup and the absence of ledger response fields/writes. `accounts.spec.js` checks desktop/mobile setup, optional income ranges and the absence of balance/receipt UI. `credit-card-monthly-journey.spec.js` checks real May purchases, June clock advancement, partial/full settlement and persisted history without duplicate spending.

Credit-card `startMonth` is optional `YYYY-MM`, the first **due month** included in bills and monthly plans. The first bill uses its full normal statement period, including purchases before that due month; purchase-month spending is unaffected. Null on creation preserves all-period coverage. An omitted/null update preserves the saved start month. Invalid format returns `400 INVALID_CREDIT_CARD`; changing a saved start month after settlements is blocked with the existing cycle-edit protection.

An explicit empty `startMonth` clears a saved start month before any settlement; omitted/null updates preserve it. Clearing is protected by the same post-settlement cycle restriction.

Accounts owns selected-month Credit-card bills: purchase/bill comparison, cycle timeline, payment progress, dated history and Record bill payment. Home provides only a compact remaining-total summary linking to this section. API and payment arithmetic are unchanged.

Income context is labeled Monthly income and combines all sources into one optional range or exact monthly estimate. No source tags or individual receipts are collected. Existing private salary-named API fields persist the combined monthly estimate compatibly.


The Accounts caller joins account `cardId` to bill `cardId` to render each selected-month bill inside its owning credit-card account. Names may differ and are not join keys. Unmatched bills remain accessible when account metadata is unavailable; no API payload or settlement behavior changes.
