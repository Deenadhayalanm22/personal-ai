CREATE TABLE user_recurring_commitment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    label VARCHAR(120) NOT NULL,
    amount_mode VARCHAR(30) NOT NULL,
    planning_amount NUMERIC(19,2) NOT NULL,
    due_day INTEGER,
    effective_month DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    category VARCHAR(100),
    subcategory VARCHAR(100),
    merchant_id BIGINT REFERENCES user_reference_entity(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_recurring_commitment_amount CHECK (planning_amount > 0),
    CONSTRAINT ck_recurring_commitment_due_day CHECK (due_day IS NULL OR due_day BETWEEN 1 AND 28)
);
CREATE INDEX idx_recurring_commitment_user ON user_recurring_commitment(user_id);
CREATE TABLE recurring_commitment_transaction (
    commitment_id BIGINT NOT NULL REFERENCES user_recurring_commitment(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL REFERENCES financial_transaction(id),
    PRIMARY KEY (commitment_id, transaction_id)
);
