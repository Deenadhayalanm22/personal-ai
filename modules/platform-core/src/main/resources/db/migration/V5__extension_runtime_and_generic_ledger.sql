CREATE TABLE extension_installation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    extension_id VARCHAR(80) NOT NULL,
    extension_version VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_extension_installation UNIQUE (tenant_id, extension_id),
    CONSTRAINT ck_extension_installation_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE TABLE core_event (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    extension_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    facts JSONB NOT NULL,
    evidence JSONB NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    causation_id VARCHAR(160),
    CONSTRAINT uq_core_event_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE core_movement (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES core_event(id),
    resource_id VARCHAR(120) NOT NULL,
    container_id VARCHAR(120) NOT NULL,
    quantity NUMERIC(19,6) NOT NULL CHECK (quantity <> 0),
    unit_id VARCHAR(40) NOT NULL
);

CREATE TABLE core_observation (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES core_event(id),
    subject_type VARCHAR(80) NOT NULL,
    subject_id VARCHAR(120) NOT NULL,
    value NUMERIC(19,6) NOT NULL,
    unit_id VARCHAR(40) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_core_event_tenant_type ON core_event(tenant_id, extension_id, event_type, occurred_at);
CREATE INDEX idx_core_movement_container ON core_movement(container_id, resource_id, unit_id);
CREATE INDEX idx_core_observation_subject ON core_observation(subject_type, subject_id, observed_at);
