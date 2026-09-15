ALTER TABLE user_reference_entity
    DROP CONSTRAINT ck_user_reference_entity_type;

ALTER TABLE user_reference_entity
    ADD CONSTRAINT ck_user_reference_entity_type
        CHECK (entity_type IN ('MERCHANT', 'BENEFICIARY', 'ACCOUNT'));

ALTER TABLE transaction_draft_extraction
    ADD COLUMN source_account_name VARCHAR(255);

ALTER TABLE financial_transaction
    ADD COLUMN source_account_id BIGINT REFERENCES user_reference_entity(id);

CREATE INDEX idx_financial_transaction_source_account
    ON financial_transaction(source_account_id)
    WHERE deleted_at IS NULL;
