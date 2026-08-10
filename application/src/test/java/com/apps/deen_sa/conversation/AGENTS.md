# Protected live-model acceptance contract

The following method is the locked live-model acceptance contract:

- `ExpenseLiveIT.it_live_complete_unique_finance_contract()` — one literal four-person contract containing:
  - Persona A: two-month multi-account financial ledger.
  - Persona B: multilingual, follow-up, multi-expense, and query flow.
  - Persona C: explicit-UPI provisional-account flow.
  - Persona D: planning/alerts plus the clean-state real-user recovery flow.

They are product/accounting contracts, not disposable model prompt tests. Their readable expected
conversation contracts live in `application/src/test/resources/live-model-transcripts/`.

AI coding agents must not edit, simplify, reorder, delete, rename, disable, weaken, regenerate, or
"fix" this test or any helper/assertion used by this test merely to make an implementation pass.
Production code must be corrected to satisfy the contract.

An AI agent may change this method or its private persona phases only when the user explicitly requests
a change to this protected test. Even then, the agent must pause before editing and obtain two separate confirmations:

1. Confirm the precise proposed test-contract change and its accounting impact.
2. After the user approves that, ask for a final confirmation that the locked acceptance contract may
   be modified.

Only after both confirmations may the agent edit the protected test. A general request to update tests,
fix failures, refactor the suite, reduce token usage, or implement a feature is not authorization.

Adding new test methods or production fixes is allowed without modifying these protected contracts.
