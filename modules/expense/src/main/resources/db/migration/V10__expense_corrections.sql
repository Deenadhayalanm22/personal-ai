ALTER TABLE state_change
    ADD COLUMN IF NOT EXISTS record_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS root_transaction_id BIGINT,
    ADD COLUMN IF NOT EXISTS replaces_transaction_id BIGINT,
    ADD COLUMN IF NOT EXISTS record_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS corrected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS correction_reason VARCHAR(100);

ALTER TABLE state_change
    ADD CONSTRAINT fk_state_change_root_transaction
        FOREIGN KEY (root_transaction_id) REFERENCES state_change(id),
    ADD CONSTRAINT fk_state_change_replaces_transaction
        FOREIGN KEY (replaces_transaction_id) REFERENCES state_change(id);

CREATE INDEX IF NOT EXISTS idx_state_change_active_expense_browse
    ON state_change (user_id, id DESC)
    WHERE transaction_type = 'EXPENSE' AND record_status = 'ACTIVE';

ALTER TABLE fin_expense_projection
    ADD COLUMN IF NOT EXISTS legacy_transaction_id BIGINT,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fin_expense_legacy_transaction
    ON fin_expense_projection (legacy_transaction_id)
    WHERE legacy_transaction_id IS NOT NULL;
