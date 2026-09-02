ALTER TABLE transaction_draft_extraction
    ALTER COLUMN amount TYPE NUMERIC(19,2)
    USING round(amount, 2);

ALTER TABLE financial_transaction
    ALTER COLUMN amount TYPE NUMERIC(19,2)
    USING round(amount, 2);
