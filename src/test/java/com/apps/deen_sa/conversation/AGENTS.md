# Protected live-model acceptance contract

`LiveModelIT.it_live_001()` is the project's locked, strict two-month financial-ledger acceptance flow.
It is a product/accounting contract, not a disposable model prompt test.

AI coding agents must not edit, simplify, reorder, delete, rename, disable, weaken, regenerate, or
"fix" this test or any helper/assertion used by this test merely to make an implementation pass.
Production code must be corrected to satisfy the contract.

An AI agent may change `it_live_001()` only when the user explicitly requests a change to this exact
protected test. Even then, the agent must pause before editing and obtain two separate confirmations:

1. Confirm the precise proposed test-contract change and its accounting impact.
2. After the user approves that, ask for a final confirmation that the locked acceptance contract may
   be modified.

Only after both confirmations may the agent edit the protected test. A general request to update tests,
fix failures, refactor the suite, reduce token usage, or implement a feature is not authorization.

Adding new test methods or production fixes is allowed without modifying this protected contract.
