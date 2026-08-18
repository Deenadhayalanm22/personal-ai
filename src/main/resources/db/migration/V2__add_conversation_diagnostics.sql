-- Short-lived MVP diagnostics: one row per customer/system turn for daily quality review.
-- This is diagnostic data rather than a source of business truth.
CREATE TABLE conversation_diagnostic_turn (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    external_message_id VARCHAR(255),
    input_kind VARCHAR(30) NOT NULL,
    input_text TEXT NOT NULL,
    response_status VARCHAR(30),
    response_text TEXT,
    response_media_type VARCHAR(100),
    response_media_filename VARCHAR(255),
    response_media_size INTEGER,
    need_followup BOOLEAN,
    active_intent VARCHAR(50),
    waiting_for_field VARCHAR(100),
    partial_json JSONB,
    saved_entity_type VARCHAR(255),
    saved_entity_json JSONB,
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    review_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_diagnostic_daily
    ON conversation_diagnostic_turn(reviewed, created_at DESC);
CREATE INDEX idx_conversation_diagnostic_user
    ON conversation_diagnostic_turn(user_id, created_at DESC);
