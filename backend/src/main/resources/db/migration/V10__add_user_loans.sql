CREATE TABLE user_loan (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    loan_name VARCHAR(255) NOT NULL,
    loan_type VARCHAR(30) NOT NULL,
    lender_name VARCHAR(255) NOT NULL,
    original_principal NUMERIC(19,2) NOT NULL CHECK (original_principal > 0),
    monthly_emi_amount NUMERIC(19,2) NOT NULL CHECK (monthly_emi_amount > 0),
    total_tenure_months INTEGER NOT NULL CHECK (total_tenure_months > 0),
    first_emi_due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_loan_type
        CHECK (loan_type IN ('HOME', 'VEHICLE', 'PERSONAL', 'EDUCATION', 'CREDIT_CARD_EMI', 'OTHER')),
    CONSTRAINT ck_user_loan_status
        CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_user_loan_user_created ON user_loan(user_id, created_at DESC);
