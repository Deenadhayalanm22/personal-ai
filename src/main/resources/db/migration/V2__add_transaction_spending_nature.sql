ALTER TABLE financial_transaction
    ADD COLUMN spending_nature VARCHAR(20);

ALTER TABLE financial_transaction
    ADD CONSTRAINT ck_financial_transaction_spending_nature
        CHECK (spending_nature IN ('ESSENTIAL', 'FLEXIBLE', 'DISCRETIONARY'));
