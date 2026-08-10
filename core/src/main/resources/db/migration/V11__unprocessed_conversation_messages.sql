CREATE TABLE unprocessed_conversation_message (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL,
    external_message_id VARCHAR(255),
    message_text TEXT NOT NULL,
    locale VARCHAR(30),
    reason VARCHAR(80) NOT NULL,
    interpreter_version VARCHAR(80),
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    occurrence_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_unprocessed_external_message
    ON unprocessed_conversation_message(channel, external_message_id)
    WHERE external_message_id IS NOT NULL;
CREATE INDEX idx_unprocessed_review_queue
    ON unprocessed_conversation_message(status, created_at);
