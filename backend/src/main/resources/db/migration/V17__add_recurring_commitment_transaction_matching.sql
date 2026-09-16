ALTER TABLE financial_transaction
    ADD COLUMN recurring_commitment_id BIGINT REFERENCES user_recurring_commitment(id);

ALTER TABLE financial_transaction
    ADD COLUMN commitment_match_status VARCHAR(20);

CREATE INDEX idx_financial_transaction_commitment_candidate
    ON financial_transaction(user_id, occurred_at DESC)
    WHERE commitment_match_status = 'CANDIDATE';
