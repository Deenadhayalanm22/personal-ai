# Unified conversation interpreter — implementation checklist

## Implemented

- [x] One central semantic interpretation contract per user turn.
- [x] Turn types for new events, pending answers, corrections, commands, queries, multiple events, and ambiguity.
- [x] Structured event patches with unresolved fields, ambiguities, field evidence, and confidence.
- [x] Context includes recent turns, pending events, last question, timezone, currency, and known accounts.
- [x] Persist pending-event state and bounded recent conversation history instead of relying only on `waitingForField`.
- [x] Keep all database writes and balance mutations outside the model.
- [x] Deterministically reject missing/invalid/non-positive financial amounts before execution.
- [x] Reuse the existing exactly-once mutation strategy and webhook idempotency.
- [x] Deterministic follow-up questions and WhatsApp buttons; the model does not compose operational questions.
- [x] Handle several interpreted events from one turn.
- [x] Handle skip/cancel commands without financial inference.
- [x] Active, shadow, and legacy rollout modes (`CONVERSATION_MODE`).
- [x] WhatsApp integration fixture starts with “I spent 35” and verifies cross-message continuity without real-model variability.
- [x] Semantic evaluation corpus separated from deterministic integration fixtures.

## Deliberately retained compatibility seams

- [x] Existing non-expense handlers remain available while domain-specific deterministic executors are migrated.
- [x] The old classifier/parser pipeline is reachable only in `legacy` mode, or for a domain that does not yet have a deterministic interpreted-event executor.
- [x] Existing session columns remain during rollout; the richer pending-event JSON is now the model-facing context.

## Deployment sequence

1. Run with `CONVERSATION_MODE=shadow`; inspect interpretation logs and expand the semantic corpus.
2. Run the deterministic WhatsApp integration suite.
3. Enable `CONVERSATION_MODE=active` for test users.
4. Compare event count, clarification rate, correction rate, invalid-output rate, and financial mutation count.
5. Expand active traffic only when mutation counts remain identical and semantic acceptance improves.

The default model is configurable with `OPENAI_INTERPRETER_MODEL`. The application currently defaults to `gpt-5.6-sol`; pin an explicit snapshot in production after evaluation if repeatability is more important than automatic model updates.
