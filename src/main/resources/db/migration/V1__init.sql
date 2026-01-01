-- Initial schema for integration tests
-- All tables required by JPA entities

-- State Container (accounts, credit cards, cash, etc.)
CREATE TABLE IF NOT EXISTS state_container (
    id BIGSERIAL PRIMARY KEY,
    owner_type VARCHAR(30) NOT NULL,
    owner_id BIGINT NOT NULL,
    container_type VARCHAR(30) NOT NULL,
    name TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(10),
    current_value NUMERIC(19,4),
    available_value NUMERIC(19,4),
    unit VARCHAR(20),
    capacity_limit NUMERIC(19,4),
    min_threshold NUMERIC(19,4),
    priority_order INTEGER,
    opened_at TIMESTAMP,
    closed_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    external_ref_type VARCHAR(30),
    external_ref_id TEXT,
    details JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    over_limit BOOLEAN DEFAULT FALSE,
    over_limit_amount NUMERIC(19,4)
);

-- State Change Records
CREATE TABLE IF NOT EXISTS state_change (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    business_id VARCHAR(255),
    transaction_type VARCHAR(50) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    quantity NUMERIC(15,4),
    unit VARCHAR(20),
    category VARCHAR(100),
    subcategory VARCHAR(100),
    main_entity VARCHAR(150),
    tx_time TIMESTAMP NOT NULL,
    raw_text TEXT,
    details JSONB,
    source_container_id BIGINT,
    target_container_id BIGINT,
    tags JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completeness_level VARCHAR(50) NOT NULL,
    financially_applied BOOLEAN NOT NULL DEFAULT FALSE,
    needs_enrichment BOOLEAN NOT NULL DEFAULT FALSE,
    application_status VARCHAR(50),
    failure_reason TEXT,
    applied_at TIMESTAMP,
    CONSTRAINT fk_statechange_source_container FOREIGN KEY (source_container_id) REFERENCES state_container(id),
    CONSTRAINT fk_statechange_target_container FOREIGN KEY (target_container_id) REFERENCES state_container(id)
);

-- State Mutations (debits/credits to containers)
CREATE TABLE IF NOT EXISTS state_mutation (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT,
    container_id BIGINT,
    adjustment_type VARCHAR(20),
    amount NUMERIC(19,4),
    reason VARCHAR(100),
    occurred_at TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT fk_mutation_statechange FOREIGN KEY (transaction_id) REFERENCES state_change(id),
    CONSTRAINT fk_mutation_container FOREIGN KEY (container_id) REFERENCES state_container(id)
);

-- Expense Records
CREATE TABLE IF NOT EXISTS expense (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT,
    amount NUMERIC(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    category VARCHAR(50) NOT NULL,
    subcategory VARCHAR(100),
    merchant_name VARCHAR(200),
    payment_method VARCHAR(50),
    spent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    raw_text TEXT,
    details JSONB,
    is_valid BOOLEAN DEFAULT TRUE,
    validation_reason TEXT,
    source VARCHAR(50),
    source_ref VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Expense Tags (many-to-many)
CREATE TABLE IF NOT EXISTS expense_tags (
    expense_id BIGINT NOT NULL,
    tag VARCHAR(255),
    CONSTRAINT fk_expense_tags FOREIGN KEY (expense_id) REFERENCES expense(id) ON DELETE CASCADE
);

-- Tag Master
CREATE TABLE IF NOT EXISTS tag_master (
    id BIGSERIAL PRIMARY KEY,
    canonical_tag VARCHAR(255),
    status VARCHAR(50)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_state_container_owner ON state_container(owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_state_container_type ON state_container(container_type);
CREATE INDEX IF NOT EXISTS idx_state_change_user ON state_change(user_id);
CREATE INDEX IF NOT EXISTS idx_state_change_type ON state_change(transaction_type);
CREATE INDEX IF NOT EXISTS idx_state_mutation_statechange ON state_mutation(transaction_id);
CREATE INDEX IF NOT EXISTS idx_state_mutation_container ON state_mutation(container_id);
CREATE INDEX IF NOT EXISTS idx_expense_user ON expense(user_id);
CREATE INDEX IF NOT EXISTS idx_expense_category ON expense(category);
CREATE INDEX IF NOT EXISTS idx_expense_spent_at ON expense(spent_at);

