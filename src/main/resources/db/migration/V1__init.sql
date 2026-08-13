-- Clean baseline for the pre-production personal expense application.

CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    locale VARCHAR(20) NOT NULL DEFAULT 'en-IN',
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_channel_external UNIQUE (channel, external_user_id)
);

CREATE TABLE conversation_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    channel VARCHAR(30) NOT NULL,
    active_transaction_id BIGINT,
    active_intent VARCHAR(50),
    waiting_for_field VARCHAR(100),
    partial_type VARCHAR(255),
    partial_json JSONB,
    pending_events_json JSONB,
    recent_turns_json JSONB,
    last_question TEXT,
    interpreter_version VARCHAR(50),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conversation_user_channel UNIQUE (user_id, channel)
);

CREATE TABLE inbound_message (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_message_id VARCHAR(255) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    CONSTRAINT uq_inbound_channel_message UNIQUE (channel, external_message_id)
);

CREATE TABLE audio_confirmation (
    id UUID PRIMARY KEY,
    whatsapp_user_id VARCHAR(255) NOT NULL,
    media_id VARCHAR(255) NOT NULL,
    transcribed_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_feature_flag (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'SUPER_ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_feature_flag_subject UNIQUE (channel, external_user_id)
);

-- Replace this test number with your own country-code-prefixed WhatsApp number before deployment.
INSERT INTO user_feature_flag (channel, external_user_id, role, enabled)
VALUES ('WHATSAPP', '919004656025', 'SUPER_ADMIN', TRUE);

CREATE TABLE state_container (
    id BIGSERIAL PRIMARY KEY,
    owner_type VARCHAR(30) NOT NULL,
    owner_id BIGINT NOT NULL,
    container_type VARCHAR(30) NOT NULL,
    name TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(10),
    current_value NUMERIC(19,4),
    available_value NUMERIC(19,4),
    unit VARCHAR(20),
    capacity_limit NUMERIC(19,4),
    min_threshold NUMERIC(19,4),
    priority_order INTEGER,
    opened_at TIMESTAMP,
    closed_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    external_ref_type VARCHAR(30),
    external_ref_id TEXT,
    details JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    over_limit BOOLEAN DEFAULT FALSE,
    over_limit_amount NUMERIC(19,4)
);

CREATE TABLE state_change (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    business_id VARCHAR(255),
    transaction_type VARCHAR(50) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    quantity NUMERIC(15,4),
    unit VARCHAR(20),
    category VARCHAR(100),
    subcategory VARCHAR(100),
    main_entity VARCHAR(150),
    tx_time TIMESTAMP NOT NULL,
    raw_text TEXT,
    details JSONB,
    source_container_id BIGINT REFERENCES state_container(id),
    target_container_id BIGINT REFERENCES state_container(id),
    tags JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completeness_level VARCHAR(50) NOT NULL,
    financially_applied BOOLEAN NOT NULL DEFAULT FALSE,
    needs_enrichment BOOLEAN NOT NULL DEFAULT FALSE,
    application_status VARCHAR(50),
    failure_reason TEXT,
    applied_at TIMESTAMP,
    record_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    root_transaction_id BIGINT REFERENCES state_change(id),
    replaces_transaction_id BIGINT REFERENCES state_change(id),
    record_version INTEGER NOT NULL DEFAULT 1,
    corrected_at TIMESTAMP,
    correction_reason VARCHAR(100)
);

CREATE TABLE state_mutation (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT REFERENCES state_change(id),
    container_id BIGINT REFERENCES state_container(id),
    adjustment_type VARCHAR(20),
    amount NUMERIC(19,4),
    reason VARCHAR(100),
    occurred_at TIMESTAMP,
    created_at TIMESTAMP
);

CREATE TABLE fin_monthly_budget (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(100) NOT NULL,
    monthly_limit NUMERIC(19,4) NOT NULL CHECK (monthly_limit > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fin_monthly_budget_user_category UNIQUE (user_id, category)
);

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

CREATE INDEX idx_conversation_user ON conversation_session(user_id);
CREATE INDEX idx_inbound_external_user ON inbound_message(external_user_id);
CREATE INDEX idx_audio_confirmation_user_status ON audio_confirmation(whatsapp_user_id, status);
CREATE INDEX idx_user_feature_flag_lookup ON user_feature_flag(channel, external_user_id, enabled);
CREATE INDEX idx_state_container_owner ON state_container(owner_type, owner_id);
CREATE INDEX idx_state_container_type ON state_container(container_type);
CREATE INDEX idx_state_change_user ON state_change(user_id);
CREATE INDEX idx_state_change_type ON state_change(transaction_type);
CREATE INDEX idx_state_change_active_expense_browse ON state_change(user_id, id DESC)
    WHERE transaction_type = 'EXPENSE' AND record_status = 'ACTIVE';
CREATE INDEX idx_state_mutation_statechange ON state_mutation(transaction_id);
CREATE INDEX idx_state_mutation_container ON state_mutation(container_id);
CREATE INDEX idx_fin_monthly_budget_user ON fin_monthly_budget(user_id) WHERE active = TRUE;
CREATE UNIQUE INDEX uq_unprocessed_external_message
    ON unprocessed_conversation_message(channel, external_message_id)
    WHERE external_message_id IS NOT NULL;
CREATE INDEX idx_unprocessed_review_queue ON unprocessed_conversation_message(status, created_at);
