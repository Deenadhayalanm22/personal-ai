# Multilingual conversation and AI-cost architecture

## Production objective

Understand English, Tamil, and romanized Tamil once per free-form turn; keep financial authorization,
calculation, persistence, and routine response rendering deterministic. The target is at least 70% fewer
model calls than a multi-classifier pipeline, measured on representative WhatsApp conversations.

## Implemented MVP checklist

- [x] One unified structured interpreter for active WhatsApp free text.
- [x] Exact current-message evidence is required before a new event amount can mutate financial state.
- [x] Removed the English read-only-question regex from the financial safety boundary.
- [x] Trusted WhatsApp buttons bypass interpretation.
- [x] Greetings/help, conversation controls, and pending numeric answers bypass interpretation.
- [x] Interpreted expense queries execute analytics and render standard responses without query-classifier
  or explanation-model calls.
- [x] Standard onboarding, clarification, controls, and expense totals use English/Tamil templates.
- [x] Interpreter output carries the detected language (`en-IN`, `ta-IN`, or `ta-Latn`).
- [x] Conversation history sent to the interpreter is capped at four recent turns.
- [x] Account context contains only id, name, type, and whether its balance is known.
- [x] Primary and low-confidence escalation models are separately configurable.
- [x] AI calls publish call, latency, input, cached-input, output, failure, and avoided-call metrics.
- [x] Regression coverage reproduces history leakage into a query and verifies no duplicate mutation.
- [x] Tamil grounding and deterministic Tamil response tests are included.

## Configuration

```text
OPENAI_MODEL=gpt-4.1-mini
OPENAI_ESCALATION_MODEL=gpt-4.1-mini
OPENAI_ESCALATION_CONFIDENCE=0.55
```

Escalation is disabled when both model names are the same. Set a stronger, different escalation model only
after evals show that the additional call materially improves low-confidence turns.

## Metrics

Actuator exposes these Micrometer series:

- `deen.ai.calls`
- `deen.ai.latency`
- `deen.ai.tokens.input`
- `deen.ai.tokens.input.cached`
- `deen.ai.tokens.output`
- `deen.ai.calls.avoided`

Use `purpose`, `model`, and `outcome` tags to build per-path cost dashboards. Do not put phone numbers,
messages, user IDs, or other high-cardinality data in metric tags.

## Next-stage checklist requiring production data

- [ ] Build a reviewed English/Tamil/Tanglish semantic evaluation set from consented, redacted messages.
- [ ] Establish accuracy, unsafe-mutation, clarification, calls-per-turn, tokens-per-turn, latency, and cost baselines.
- [ ] Retrieve only relevant taxonomy candidates if the category catalogue becomes large.
- [ ] Distill or fine-tune a smaller interpreter only after enough reviewed examples exist.
- [ ] Add user-facing language preference controls and persist `ta-IN` versus `ta-Latn` preference.
- [ ] Move the remaining account, credit-card, and expense follow-up/confirmation strings into the message catalogue.
- [ ] Enable a stronger escalation model only when evaluation results justify its cost.
- [ ] Use batch processing for offline insights/evaluations, not latency-sensitive WhatsApp turns.
