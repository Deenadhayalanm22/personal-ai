CREATE TABLE expense_daily_aggregate (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    aggregate_date DATE NOT NULL,
    category VARCHAR(100) NOT NULL,
    subcategory VARCHAR(100) NOT NULL,
    spending_nature VARCHAR(30) NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL,
    transaction_count INTEGER NOT NULL,
    min_amount NUMERIC(15,2),
    max_amount NUMERIC(15,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expense_daily_aggregate_dimension UNIQUE (
        user_id,
        aggregate_date,
        category,
        subcategory,
        spending_nature
    ),
    CONSTRAINT ck_expense_daily_aggregate_spending_nature
        CHECK (spending_nature IN ('ESSENTIAL', 'FLEXIBLE', 'DISCRETIONARY')),
    CONSTRAINT ck_expense_daily_aggregate_transaction_count
        CHECK (transaction_count > 0)
);

CREATE INDEX idx_expense_daily_aggregate_date_user
    ON expense_daily_aggregate(aggregate_date, user_id);
