ALTER TABLE transaction_draft DROP CONSTRAINT ck_transaction_draft_status;
ALTER TABLE transaction_draft ADD CONSTRAINT ck_transaction_draft_status
    CHECK (status IN ('PENDING', 'TRANSCRIPT_REVIEW', 'CONSUMED', 'EXPIRED', 'CANCELLED'));
