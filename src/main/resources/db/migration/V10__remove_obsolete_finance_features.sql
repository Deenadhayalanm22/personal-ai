DROP TABLE IF EXISTS fin_monthly_budget;

DROP TABLE IF EXISTS account_enrichment_preference;
DROP TABLE IF EXISTS state_mutation;

ALTER TABLE state_change
    DROP COLUMN IF EXISTS source_container_id,
    DROP COLUMN IF EXISTS target_container_id,
    DROP COLUMN IF EXISTS financially_applied,
    DROP COLUMN IF EXISTS needs_enrichment;

DROP TABLE IF EXISTS state_container;

ALTER TABLE conversation_diagnostic_turn
    DROP COLUMN IF EXISTS response_media_type,
    DROP COLUMN IF EXISTS response_media_filename,
    DROP COLUMN IF EXISTS response_media_size;
