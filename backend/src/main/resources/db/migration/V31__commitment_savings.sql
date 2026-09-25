CREATE TABLE commitment_savings_plan (
    id BIGSERIAL PRIMARY KEY,
    commitment_id BIGINT NOT NULL REFERENCES user_recurring_commitment(id) ON DELETE CASCADE,
    target_date DATE NOT NULL,
    target_amount NUMERIC(19,2) NOT NULL,
    start_month DATE NOT NULL,
    monthly_amount NUMERIC(19,2) NOT NULL,
    final_amount NUMERIC(19,2) NOT NULL,
    used_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_commitment_savings_target UNIQUE (commitment_id, target_date)
);

CREATE TABLE commitment_savings_entry (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES commitment_savings_plan(id) ON DELETE CASCADE,
    scheduled_month DATE NOT NULL,
    status VARCHAR(12) NOT NULL,
    amount NUMERIC(19,2),
    recorded_at DATE,
    CONSTRAINT uq_commitment_savings_month UNIQUE (plan_id, scheduled_month)
);

ALTER TABLE recurring_commitment_occurrence ADD COLUMN savings_used NUMERIC(19,2);
