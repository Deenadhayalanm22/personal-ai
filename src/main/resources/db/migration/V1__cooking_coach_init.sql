CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    locale VARCHAR(20) NOT NULL DEFAULT 'en-IN',
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_channel_external UNIQUE (channel, external_user_id)
);

CREATE TABLE inbound_message (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_message_id VARCHAR(255) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_inbound_channel_message UNIQUE (channel, external_message_id)
);

CREATE TABLE user_feature_flag (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_feature_flag_subject UNIQUE (channel, external_user_id)
);

CREATE TABLE audio_confirmation (
    id UUID PRIMARY KEY,
    whatsapp_user_id VARCHAR(255) NOT NULL,
    media_id VARCHAR(255) NOT NULL,
    transcribed_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cooking_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    recipe_id VARCHAR(100) NOT NULL,
    recipe_version INTEGER NOT NULL,
    rice_grams NUMERIC(10,1) NOT NULL,
    chicken_grams NUMERIC(10,1) NOT NULL,
    current_step INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    adjustment_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cooking_session_user_status ON cooking_session(user_id, status, updated_at DESC);

-- Grant pilot access explicitly after deployment:
-- INSERT INTO user_feature_flag(channel, external_user_id, role, enabled)
-- VALUES ('WHATSAPP', '919999999999', 'SUPER_ADMIN', true);
