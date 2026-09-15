# Personal AI repository rules

## Feature documentation is mandatory

The cross-stack functional source of truth is `docs/jira/personal-expense/`. API details are in `docs/contracts/`.

Before changing a Java class that has a `FIN-EPIC-*` Javadoc, or a frontend module with a `FIN-EPIC-*` header comment:

1. Read the linked Jira epic.
2. Read the relevant contract when the change affects an endpoint, payload, response, validation rule, error, authentication, or caller.
3. Update the Jira epic in the same change when behavior, user flow, state change, or integration-test expectation changes.
4. Update the contract in the same change when the interface changes.
5. Add or update tests for the affected acceptance criterion.

Do not use `docs/jira/core/` as an active feature specification; it contains historical proposals. Do not add behavior that is not supported by the current code and documented in the active Personal Expense epics.
