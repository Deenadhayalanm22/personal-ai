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

CREATE INDEX idx_magic_link_user_created
    ON magic_link(user_id, created_at DESC);
