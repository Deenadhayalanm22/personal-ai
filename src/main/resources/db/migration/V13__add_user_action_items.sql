CREATE TABLE user_action_item (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    action_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reference_type VARCHAR(50) NOT NULL,
    reference_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    scheduled_completion_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_user_action_item_type
        CHECK (action_type IN ('LOAN_CLOSURE_CONFIRMATION')),
    CONSTRAINT ck_user_action_item_status
        CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT ck_user_action_item_reference_type
        CHECK (reference_type IN ('LOAN'))
);

CREATE UNIQUE INDEX uq_user_action_item_open_loan_closure
    ON user_action_item(user_id, action_type, reference_type, reference_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_user_action_item_open_user_created
    ON user_action_item(user_id, created_at DESC)
    WHERE status = 'OPEN';
