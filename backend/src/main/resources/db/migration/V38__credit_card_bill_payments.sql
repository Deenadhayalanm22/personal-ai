CREATE TABLE credit_card_bill_payment (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES user_credit_card(id),
    due_month DATE NOT NULL,
    statement_end DATE NOT NULL,
    paid_at DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (card_id, request_id)
);
CREATE INDEX idx_card_payment_statement ON credit_card_bill_payment(card_id, statement_end);
