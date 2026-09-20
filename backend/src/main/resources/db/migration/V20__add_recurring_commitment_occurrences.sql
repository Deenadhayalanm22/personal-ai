CREATE TABLE recurring_commitment_occurrence (
    id BIGSERIAL PRIMARY KEY,
    commitment_id BIGINT NOT NULL REFERENCES user_recurring_commitment(id) ON DELETE CASCADE,
    scheduled_month DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    completed_at DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recurring_commitment_occurrence UNIQUE (commitment_id, scheduled_month)
);
