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
