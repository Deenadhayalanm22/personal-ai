CREATE TABLE fin_expense_projection (
    id BIGSERIAL PRIMARY KEY,
    core_event_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    category VARCHAR(100),
    subcategory VARCHAR(100),
    source_account VARCHAR(255),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_fin_expense_event UNIQUE (core_event_id),
    CONSTRAINT fk_fin_expense_event FOREIGN KEY (core_event_id) REFERENCES core_event(id)
);

CREATE INDEX idx_fin_expense_user_time ON fin_expense_projection(user_id, occurred_at);
CREATE INDEX idx_fin_expense_tenant_time ON fin_expense_projection(tenant_id, occurred_at);
