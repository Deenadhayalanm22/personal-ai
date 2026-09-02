ALTER TABLE transaction_draft
    ADD COLUMN pending_action_context_id VARCHAR(40)
        REFERENCES pending_action_context(id);

CREATE INDEX idx_transaction_draft_pending_action_context
    ON transaction_draft(pending_action_context_id)
    WHERE pending_action_context_id IS NOT NULL;
