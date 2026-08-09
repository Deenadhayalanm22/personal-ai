CREATE TABLE user_feature_flag (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL,
    external_user_id VARCHAR(100) NOT NULL,
    feature_key VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_feature_flag_subject UNIQUE (channel, external_user_id, feature_key)
);

CREATE INDEX idx_user_feature_flag_lookup
    ON user_feature_flag (channel, external_user_id, feature_key, enabled);
