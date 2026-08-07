CREATE TABLE extension_installation_audit (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    extension_id VARCHAR(80) NOT NULL,
    extension_version VARCHAR(40) NOT NULL,
    action VARCHAR(30) NOT NULL CHECK (action IN ('ENABLED', 'DISABLED')),
    configuration JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_extension_audit_tenant ON extension_installation_audit(tenant_id, occurred_at);
