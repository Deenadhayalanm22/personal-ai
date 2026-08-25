CREATE TABLE expense_draft (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source_channel VARCHAR(30) NOT NULL,
    source_message_id VARCHAR(255),
    source_session_id BIGINT UNIQUE REFERENCES conversation_session(id) ON DELETE SET NULL,
    raw_text TEXT NOT NULL,
    partial_json JSONB NOT NULL,
    missing_fields JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    discarded_at TIMESTAMP,
    completed_transaction_id BIGINT REFERENCES state_change(id),
    CONSTRAINT chk_expense_draft_status CHECK (status IN ('PENDING', 'COMPLETED', 'DISCARDED')),
    CONSTRAINT uq_expense_draft_source_message UNIQUE (source_channel, source_message_id)
);

CREATE INDEX idx_expense_draft_user_pending
    ON expense_draft(user_id, updated_at DESC) WHERE status = 'PENDING';

ALTER TABLE conversation_session
    ADD COLUMN active_draft_id BIGINT REFERENCES expense_draft(id);

-- Preserve unanswered expense captures that were already stored only in conversation state.
INSERT INTO expense_draft (
    user_id, source_channel, source_session_id, raw_text, partial_json, missing_fields,
    status, version, created_at, updated_at)
SELECT
    user_id,
    channel,
    id,
    COALESCE(partial_json ->> 'rawText', ''),
    partial_json,
    CASE
        WHEN waiting_for_field IS NULL THEN '[]'::jsonb
        ELSE jsonb_build_array(waiting_for_field)
    END,
    'PENDING',
    1,
    updated_at,
    updated_at
FROM conversation_session
WHERE UPPER(active_intent) = 'EXPENSE'
  AND partial_json IS NOT NULL;

UPDATE conversation_session session
SET active_draft_id = draft.id
FROM expense_draft draft
WHERE draft.source_session_id = session.id;
