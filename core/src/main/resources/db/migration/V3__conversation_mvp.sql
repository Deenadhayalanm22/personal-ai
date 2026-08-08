CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    locale VARCHAR(20) NOT NULL DEFAULT 'en-IN',
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_channel_external UNIQUE (channel, external_user_id)
);

CREATE TABLE IF NOT EXISTS conversation_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL,
    active_transaction_id BIGINT,
    active_intent VARCHAR(50),
    waiting_for_field VARCHAR(100),
    partial_type VARCHAR(255),
    partial_json JSONB,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conversation_user_channel UNIQUE (user_id, channel),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE IF NOT EXISTS inbound_message (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_message_id VARCHAR(255) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    CONSTRAINT uq_inbound_channel_message UNIQUE (channel, external_message_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_user ON conversation_session(user_id);
CREATE INDEX IF NOT EXISTS idx_inbound_external_user ON inbound_message(external_user_id);
