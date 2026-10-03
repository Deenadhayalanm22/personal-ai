-- Account references remain the single identity used by expense capture.
CREATE TABLE bank_account_profile (
    account_reference_id BIGINT PRIMARY KEY REFERENCES user_reference_entity(id),
    opening_amount NUMERIC(19,2) CHECK (opening_amount >= 0),
    opening_date DATE,
    CHECK ((opening_amount IS NULL) = (opening_date IS NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE account_money_received (
    id BIGSERIAL PRIMARY KEY,
    account_reference_id BIGINT NOT NULL REFERENCES bank_account_profile(account_reference_id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    received_at DATE NOT NULL,
    label VARCHAR(120) NOT NULL,
    request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(account_reference_id, request_id)
);
ALTER TABLE credit_card_bill_payment ADD COLUMN funding_account_reference_id BIGINT REFERENCES bank_account_profile(account_reference_id);
CREATE INDEX idx_account_received_date ON account_money_received(account_reference_id,received_at);
CREATE INDEX idx_card_payment_funding ON credit_card_bill_payment(funding_account_reference_id,paid_at);
