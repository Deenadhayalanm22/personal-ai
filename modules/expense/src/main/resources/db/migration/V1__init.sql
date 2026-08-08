-- Initial schema for the application and integration tests.

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

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_state_container_owner ON state_container(owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_state_container_type ON state_container(container_type);
CREATE INDEX IF NOT EXISTS idx_state_change_user ON state_change(user_id);
CREATE INDEX IF NOT EXISTS idx_state_change_type ON state_change(transaction_type);
CREATE INDEX IF NOT EXISTS idx_state_mutation_statechange ON state_mutation(transaction_id);
CREATE INDEX IF NOT EXISTS idx_state_mutation_container ON state_mutation(container_id);

