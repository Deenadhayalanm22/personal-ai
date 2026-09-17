CREATE TABLE loan_emi_occurrence (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES user_loan(id),
    due_month DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_amount NUMERIC(19,2),
    paid_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_loan_emi_occurrence_month UNIQUE (loan_id, due_month)
);
