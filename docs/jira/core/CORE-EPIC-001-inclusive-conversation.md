# CORE-EPIC-001 — Inclusive channel and conversation experience

| Field | Value |
|---|---|
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Complete simple tasks through understandable WhatsApp voice or text without forms |

## CORE-001 — Accept voice and text consistently

**Status:** In Progress · **Priority:** P0

**As a** user with limited typing ability, **I want** to speak or type naturally **so that** I can record work without learning a form.

### Acceptance criteria

1. **Given** supported text or audio, **when** received, **then** both produce the same channel-neutral turn contract with original evidence retained.
2. **Given** audio transcription is uncertain, **when** a consequential value may be wrong, **then** the assistant reads back the key facts and asks for confirmation before execution.
3. **Given** media failure or unsupported audio, **when** processing stops, **then** the user receives a short recovery instruction in their language and no movement occurs.
4. **Given** a response, **when** delivered, **then** it is short enough for chat/read-aloud use and does not rely on tables or technical terms.

## CORE-002 — Understand a turn with bounded context

**Status:** Done · **Priority:** P0

### Acceptance criteria

1. A free-form turn produces a turn type, intent, event patches, fields, units, evidence, unresolved fields, ambiguity, and confidence.
2. Context includes only bounded recent turns, pending events, last question, user locale, and extension-provided reference summaries.
3. Invalid structured output is rejected and cannot silently become an event.
4. Several events in one message remain independently identifiable and executable.

## CORE-003 — Ask progressive follow-up questions

**Status:** In Progress · **Priority:** P0

### Acceptance criteria

1. **Given** an extension's required field is missing, **when** the known event can be retained, **then** it is saved as pending and one clear question is asked.
2. The extension declares required fields, question order, choices, skippable fields, and completion rules; the core does not hard-code them.
3. `skip`, `later`, `not sure`, and equivalent localized answers retain permitted facts without inventing an answer.
4. `cancel` or `stop` ends the question flow; a new event-like sentence interrupts it and starts a new event.
5. Trusted button IDs are commands; translated visible labels do not determine behavior.

## CORE-004 — Correct and undo conversationally

**Status:** To Do · **Priority:** P1

### Acceptance criteria

1. The user can identify the last or a clearly described event and change an extension-approved field through conversation.
2. A correction preserves the original audit history and creates compensating movements where required.
3. Undo creates an auditable reversal; it never deletes the original event.
4. Ambiguous targets require clarification before any correction or reversal.

## CORE-005 — Localize for actual users

**Status:** In Progress · **Priority:** P1

### Acceptance criteria

1. English, Tamil script, and romanized Tamil are detected or selected independently.
2. A persisted language preference survives code-switching and can be changed by voice/text.
3. Prompts, choices, confirmations, errors, units, numbers, and summaries have reviewed localized templates.
4. Comprehension and completion are tested with intended users, including low-literacy voice-first participants.
