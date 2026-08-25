CREATE TABLE tag (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_tag_user_normalized_name UNIQUE (user_id, normalized_name)
);

CREATE TABLE transaction_tag (
    transaction_id BIGINT NOT NULL REFERENCES state_change(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (transaction_id, tag_id)
);

CREATE INDEX idx_transaction_tag_tag_id ON transaction_tag(tag_id, transaction_id);
