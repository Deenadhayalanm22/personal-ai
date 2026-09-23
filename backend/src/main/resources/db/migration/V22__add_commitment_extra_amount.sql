ALTER TABLE recurring_commitment_occurrence ADD COLUMN extra_amount DECIMAL(19, 2);

CREATE TABLE recurring_commitment_extra (
    id BIGSERIAL PRIMARY KEY,
    occurrence_id BIGINT NOT NULL REFERENCES recurring_commitment_occurrence(id) ON DELETE CASCADE,
    amount DECIMAL(19, 2) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
