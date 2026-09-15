# Usage report regression TODO — 2026-08-18

- [x] A complete new expense interrupts a pending expense instead of being swallowed by it.
- [x] Temporal suffixes such as `yesterday` and `on Saturday` never become account names.
- [x] `yesterday`, `today`, and named weekdays resolve to the intended transaction date.
- [x] Generic UPI, bank-account, cash, and card wording maps to a supported account type.
- [x] Invalid names such as `TODAY` and `Saturday from bank account` cannot create accounts.
- [x] `/start`, flexible spending-summary wording, and last-transaction browsing are recognized.
- [x] School bags resolve to School Supplies; ordinary meat/fish groceries do not become celebration meals.
- [x] Money sent to another person remains an expense unless both accounts are explicitly user-owned.
- [x] `transferred 500 to my father paid using upi` retains UPI and skips the redundant payment follow-up.
- [x] Unsupported currency/category-splitting requests receive an honest review response.
- [x] Add a separate live-model regression scenario without changing the protected master contract.
- [x] Focused unit tests and full test compilation pass.
- [ ] Execute the gated live-model suite with `RUN_LIVE_MODEL_TESTS=true` and a real `OPENAI_API_KEY`.

## Verification note

The broad `mvn test` run is currently blocked by the pre-existing default-profile datasource value:
`DeenSaApplicationTests` fails during Flyway startup because its URL does not begin with `jdbc`.
