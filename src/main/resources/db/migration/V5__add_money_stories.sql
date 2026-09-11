ALTER TABLE expense_daily_aggregate
    ALTER COLUMN user_id TYPE BIGINT USING user_id::BIGINT;

ALTER TABLE expense_daily_aggregate
    ADD CONSTRAINT fk_expense_daily_aggregate_user
        FOREIGN KEY (user_id) REFERENCES app_user(id);

CREATE TABLE expense_daily_reference_aggregate (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    aggregate_date DATE NOT NULL,
    reference_entity_id BIGINT NOT NULL REFERENCES user_reference_entity(id),
    reference_type VARCHAR(50) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    transaction_count INTEGER NOT NULL CHECK (transaction_count > 0),
    min_amount NUMERIC(19,2),
    max_amount NUMERIC(19,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_expense_daily_reference_aggregate UNIQUE
        (user_id, aggregate_date, reference_entity_id)
);

CREATE INDEX idx_expense_daily_reference_aggregate_user_date
    ON expense_daily_reference_aggregate(user_id, aggregate_date, reference_type);

CREATE TABLE money_story_snapshot (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    scope_month DATE NOT NULL,
    timezone VARCHAR(60) NOT NULL,
    locale VARCHAR(20) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_watermark TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    superseded_at TIMESTAMPTZ,
    calculation_version INTEGER NOT NULL,
    CONSTRAINT ck_money_story_snapshot_scope_month CHECK (scope_month = date_trunc('month', scope_month)::date),
    CONSTRAINT ck_money_story_snapshot_status CHECK (status IN ('READY', 'STALE', 'FAILED'))
);

CREATE UNIQUE INDEX uq_money_story_snapshot_current
    ON money_story_snapshot(user_id, scope_month)
    WHERE superseded_at IS NULL;
CREATE INDEX idx_money_story_snapshot_user_month
    ON money_story_snapshot(user_id, scope_month DESC, generated_at DESC);

CREATE TABLE money_story (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES money_story_snapshot(id) ON DELETE CASCADE,
    story_key VARCHAR(180) NOT NULL,
    story_type VARCHAR(60) NOT NULL,
    rule_version INTEGER NOT NULL,
    template_version INTEGER NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    impact_amount NUMERIC(19,2) NOT NULL,
    payload TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    CONSTRAINT ck_money_story_period CHECK (period_end >= period_start),
    CONSTRAINT uq_money_story_snapshot_key UNIQUE(snapshot_id, story_key)
);
CREATE INDEX idx_money_story_snapshot_impact ON money_story(snapshot_id, impact_amount DESC);

CREATE TABLE money_story_evidence (
    story_id UUID NOT NULL REFERENCES money_story(id) ON DELETE CASCADE,
    ordinal SMALLINT NOT NULL,
    transaction_id BIGINT NOT NULL REFERENCES financial_transaction(id),
    amount NUMERIC(19,2) NOT NULL,
    occurred_at DATE NOT NULL,
    merchant_label VARCHAR(255),
    category_label VARCHAR(100),
    PRIMARY KEY(story_id, ordinal),
    CONSTRAINT uq_money_story_evidence_transaction UNIQUE(story_id, transaction_id)
);
