# EPIC-005 — Channels, identity, and reliable delivery

| Field | Value |
|---|---|
| Issue type | Epic |
| Parent | INIT-001 |
| Status | In Progress |
| Goal | Provide consistent, isolated, retry-safe conversations across WhatsApp, REST, and audio |

## STORY-021 — Receive and reply to WhatsApp messages

**Status:** In Progress · **Priority:** P0

**As a** WhatsApp user, **I want** messages processed and answered reliably **so that** the assistant works in my normal chat flow.

### Acceptance criteria

1. **Given** a valid webhook verification request, **when** the configured challenge is checked, **then** verification succeeds only for the configured token.
2. **Given** a supported inbound text or trusted interactive action, **when** received, **then** it enters the unified conversation path and a reply is sent.
3. **Given** an unsupported payload, **when** received, **then** it is safely ignored or answered without a server error or mutation.
4. **Given** outbound delivery failure, **when** retries occur, **then** the original inbound event is not re-executed financially.

## STORY-022 — Deduplicate inbound delivery

**Status:** Done · **Priority:** P0

**As a** user, **I want** provider retries handled once **so that** duplicate webhooks do not duplicate transactions.

### Acceptance criteria

1. **Given** an inbound WhatsApp message ID, **when** first received, **then** its processing state is persisted.
2. **Given** the same provider message ID again, **when** received, **then** it cannot create another financial event or mutation.
3. **Given** processing fails transiently, **when** retried, **then** the persisted state supports safe recovery rather than permanent silent loss.

## STORY-023 — Isolate users and conversations

**Status:** In Progress · **Priority:** P0

**As a** user, **I want** my identity, accounts, history, and conversation isolated **so that** another person cannot access or change my data.

### Acceptance criteria

1. **Given** different WhatsApp sender IDs, **when** resolved, **then** they map to separate internal users and sessions.
2. **Given** a repository query or mutation, **when** executed, **then** ownership scope is enforced at every access path.
3. **Given** a user-controlled account/event identifier belonging to someone else, **when** supplied, **then** access is denied without revealing its existence.
4. **Given** REST access, **when** used outside a trusted development environment, **then** authenticated identity—not a client-provided user ID alone—sets ownership.

### Sub-tasks

- STORY-023-A — Threat-model tenant isolation and enumerate every repository access path.
- STORY-023-B — Add authentication and centralized ownership authorization.
- STORY-023-C — Add cross-user negative integration tests.

## STORY-024 — Accept audio with explicit confirmation

**Status:** In Progress · **Priority:** P1

**As a** user, **I want** to speak a transaction and confirm its transcription **so that** recognition errors do not silently affect finances.

### Acceptance criteria

1. **Given** supported WhatsApp audio, **when** downloaded and transcribed, **then** the transcript is associated with the inbound user/message.
2. **Given** a transaction-capable transcript, **when** confirmation is required, **then** no mutation occurs before an unexpired confirmation.
3. **Given** rejection or expiry, **when** processed, **then** no financial mutation occurs and temporary media follows retention policy.
4. **Given** download or transcription failure, **when** reported, **then** secrets and media URLs are not exposed.

## STORY-025 — Provide a stable REST integration surface

**Status:** In Progress · **Priority:** P1

**As an** integration client, **I want** a versioned processing API and health endpoint **so that** non-WhatsApp clients can use and monitor the service.

### Acceptance criteria

1. `POST /api/v1/process` validates input and returns a stable status/response contract.
2. `GET /health` reports service health without leaking configuration or dependencies' secrets.
3. Authentication, authorization, rate limits, request size, timeout, and idempotency behavior are defined before public exposure.
4. API failures use safe, actionable error responses and correlation IDs.
