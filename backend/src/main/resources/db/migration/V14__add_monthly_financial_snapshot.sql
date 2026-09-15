CREATE TABLE monthly_financial_snapshot (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    scope_month DATE NOT NULL,
    calculation_version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_monthly_financial_snapshot_user_month UNIQUE (user_id, scope_month),
    CONSTRAINT ck_monthly_financial_snapshot_scope_month CHECK (scope_month = date_trunc('month', scope_month)::date)
);

CREATE INDEX idx_monthly_financial_snapshot_user_month
    ON monthly_financial_snapshot(user_id, scope_month DESC);
