CREATE TABLE account_enrichment_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES state_container(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    prompt_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    remind_after TIMESTAMPTZ,
    last_prompted_at TIMESTAMPTZ,
    prompt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_enrichment_field UNIQUE (account_id, field_name),
    CONSTRAINT ck_account_enrichment_status CHECK (
        prompt_status IN ('PENDING', 'SNOOZED', 'AUTO_PROMPT_DISABLED', 'COMPLETED')
    )
);

CREATE INDEX idx_account_enrichment_due
    ON account_enrichment_preference(user_id, prompt_status, remind_after);
