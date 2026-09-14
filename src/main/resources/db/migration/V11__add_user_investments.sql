CREATE TABLE user_investment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    asset_type VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_instrument_id VARCHAR(100) NOT NULL,
    display_name_snapshot VARCHAR(255) NOT NULL,
    isin_snapshot VARCHAR(30),
    exchange VARCHAR(30),
    sip_amount NUMERIC(19,2),
    sip_day INTEGER,
    sip_start_month DATE,
    sip_status VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_investment_instrument UNIQUE (user_id, asset_type, provider, external_instrument_id),
    CONSTRAINT ck_user_investment_asset_type CHECK (asset_type IN ('MUTUAL_FUND', 'STOCK')),
    CONSTRAINT ck_user_investment_sip_day CHECK (sip_day IS NULL OR sip_day BETWEEN 1 AND 28),
    CONSTRAINT ck_user_investment_sip CHECK ((sip_amount IS NULL AND sip_day IS NULL AND sip_start_month IS NULL AND sip_status IS NULL)
        OR (sip_amount > 0 AND sip_day IS NOT NULL AND sip_start_month IS NOT NULL AND sip_status IS NOT NULL))
);
CREATE INDEX idx_user_investment_user_created ON user_investment(user_id, created_at DESC);

CREATE TABLE investment_transaction (
    id BIGSERIAL PRIMARY KEY,
    user_investment_id BIGINT NOT NULL REFERENCES user_investment(id),
    transaction_kind VARCHAR(30) NOT NULL,
    scheduled_month DATE,
    status VARCHAR(20) NOT NULL,
    transaction_date DATE,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    unit_price NUMERIC(19,6),
    units NUMERIC(19,6),
    calculation_source VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_investment_transaction_kind CHECK (transaction_kind IN ('OPENING_BALANCE', 'SIP', 'LUMPSUM', 'BUY', 'SELL')),
    CONSTRAINT ck_investment_transaction_status CHECK (status IN ('SCHEDULED', 'DUE', 'PENDING', 'CONFIRMED', 'SKIPPED', 'FAILED')),
    CONSTRAINT ck_investment_calculation_source CHECK (calculation_source IS NULL OR calculation_source IN ('USER_ENTERED', 'NAV_ESTIMATED', 'STATEMENT_VERIFIED')),
    CONSTRAINT ck_investment_transaction_confirmed CHECK ((status = 'CONFIRMED' AND transaction_date IS NOT NULL AND units IS NOT NULL AND calculation_source IS NOT NULL)
        OR status <> 'CONFIRMED')
);
CREATE INDEX idx_investment_transaction_investment_created ON investment_transaction(user_investment_id, created_at);
CREATE UNIQUE INDEX uq_investment_sip_month ON investment_transaction(user_investment_id, scheduled_month)
    WHERE transaction_kind = 'SIP' AND scheduled_month IS NOT NULL;
