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
