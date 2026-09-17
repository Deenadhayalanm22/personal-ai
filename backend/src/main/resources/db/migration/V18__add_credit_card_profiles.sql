CREATE TABLE user_credit_card (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    account_reference_id BIGINT NOT NULL REFERENCES user_reference_entity(id),
    card_name VARCHAR(120) NOT NULL,
    issuer_name VARCHAR(120) NOT NULL,
    statement_day INTEGER NOT NULL,
    due_day INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_credit_card_account UNIQUE (user_id, account_reference_id),
    CONSTRAINT ck_user_credit_card_statement_day CHECK (statement_day BETWEEN 1 AND 28),
    CONSTRAINT ck_user_credit_card_due_day CHECK (due_day BETWEEN 1 AND 28)
);
CREATE INDEX idx_user_credit_card_user_active ON user_credit_card(user_id, active);
