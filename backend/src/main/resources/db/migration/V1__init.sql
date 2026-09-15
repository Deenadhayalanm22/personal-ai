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

CREATE INDEX idx_user_feature_flag_lookup
    ON user_feature_flag(channel, external_user_id, enabled);

-- Replace this test number with your own country-code-prefixed WhatsApp number before deployment.
INSERT INTO user_feature_flag (channel, external_user_id, role, enabled)
VALUES ('WHATSAPP', '919004656025', 'SUPER_ADMIN', TRUE);

CREATE TABLE magic_link (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_magic_link_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_magic_link_user_created ON magic_link(user_id, created_at DESC);

CREATE TABLE web_session (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_web_session_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_web_session_user_created ON web_session(user_id, created_at DESC);

CREATE TABLE pending_action_context (
    id VARCHAR(40) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    context_type VARCHAR(50) NOT NULL,
    context_value VARCHAR(500) NOT NULL,
    timezone VARCHAR(60),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    replaced_at TIMESTAMP
);

CREATE UNIQUE INDEX uq_pending_action_context_active_user
    ON pending_action_context(user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_pending_action_context_user_expiry
    ON pending_action_context(user_id, expires_at);

CREATE TABLE transaction_draft (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    input_type VARCHAR(20) NOT NULL,
    source VARCHAR(30) NOT NULL,
    source_message_id VARCHAR(255) NOT NULL,
    raw_text TEXT,
    transcribed_text TEXT,
    normalized_text TEXT,
    pending_action_context_id VARCHAR(40) REFERENCES pending_action_context(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_transaction_draft_status
        CHECK (status IN ('PENDING', 'CONSUMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT uq_transaction_draft_source_message UNIQUE (source, source_message_id)
);

CREATE INDEX idx_transaction_draft_user_status_created
    ON transaction_draft(user_id, status, created_at);

CREATE INDEX idx_transaction_draft_pending_action_context
    ON transaction_draft(pending_action_context_id)
    WHERE pending_action_context_id IS NOT NULL;

CREATE TABLE transaction_draft_extraction (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL REFERENCES transaction_draft(id),
    amount NUMERIC(19,2),
    merchant_name VARCHAR(255),
    category_id VARCHAR(100),
    subcategory_id VARCHAR(100),
    occurred_at DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    confidence NUMERIC(5,4) CHECK (confidence >= 0 AND confidence <= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_transaction_draft_extraction_status
        CHECK (status IN ('ACTIVE', 'USED', 'REJECTED')),
    CONSTRAINT uq_transaction_draft_extraction_draft UNIQUE (draft_id)
);

CREATE INDEX idx_transaction_draft_extraction_draft
    ON transaction_draft_extraction(draft_id);

CREATE TABLE user_reference_entity (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    entity_type VARCHAR(50) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_reference_entity_type
        CHECK (entity_type IN ('MERCHANT', 'BENEFICIARY'))
);

CREATE UNIQUE INDEX uq_user_reference_entity_identity
    ON user_reference_entity(user_id, entity_type, lower(canonical_name));

CREATE INDEX idx_user_reference_entity_user_type_active
    ON user_reference_entity(user_id, entity_type, active);

CREATE TABLE user_reference_alias (
    id BIGSERIAL PRIMARY KEY,
    reference_entity_id BIGINT NOT NULL REFERENCES user_reference_entity(id) ON DELETE CASCADE,
    alias_text VARCHAR(255) NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_user_reference_alias_text
    ON user_reference_alias(reference_entity_id, lower(alias_text));

CREATE INDEX idx_user_reference_alias_lookup
    ON user_reference_alias(lower(alias_text));

CREATE TABLE financial_transaction (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    occurred_at DATE NOT NULL,
    category VARCHAR(100),
    subcategory VARCHAR(100),
    merchant_id BIGINT REFERENCES user_reference_entity(id),
    source_draft_id BIGINT NOT NULL REFERENCES transaction_draft(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_financial_transaction_source_draft UNIQUE (source_draft_id)
);

CREATE INDEX idx_financial_transaction_user_occurred
    ON financial_transaction(user_id, occurred_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_financial_transaction_merchant
    ON financial_transaction(merchant_id)
    WHERE deleted_at IS NULL;
