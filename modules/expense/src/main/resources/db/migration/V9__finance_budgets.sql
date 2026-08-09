CREATE TABLE fin_monthly_budget (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(100) NOT NULL,
    monthly_limit NUMERIC(19,4) NOT NULL CHECK (monthly_limit > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fin_monthly_budget_user_category UNIQUE (user_id, category)
);

CREATE INDEX idx_fin_monthly_budget_user ON fin_monthly_budget(user_id) WHERE active = TRUE;
