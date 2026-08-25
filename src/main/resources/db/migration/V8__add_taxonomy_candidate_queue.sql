CREATE TABLE taxonomy_candidate (
    id BIGSERIAL PRIMARY KEY,
    proposed_category VARCHAR(100) NOT NULL,
    proposed_subcategory VARCHAR(100) NOT NULL,
    normalized_category VARCHAR(100) NOT NULL,
    normalized_subcategory VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    occurrence_count INTEGER NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    approved_category VARCHAR(100),
    approved_subcategory VARCHAR(100),
    CONSTRAINT uq_taxonomy_candidate_normalized
        UNIQUE (normalized_category, normalized_subcategory),
    CONSTRAINT chk_taxonomy_candidate_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'MERGED'))
);

CREATE TABLE taxonomy_candidate_occurrence (
    candidate_id BIGINT NOT NULL REFERENCES taxonomy_candidate(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL REFERENCES state_change(id) ON DELETE CASCADE,
    item_concept VARCHAR(150),
    confidence NUMERIC(5,4),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (candidate_id, transaction_id),
    CONSTRAINT chk_taxonomy_candidate_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_taxonomy_candidate_review
    ON taxonomy_candidate(status, occurrence_count DESC, last_seen_at DESC);
