CREATE TABLE cooking_setup (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    stage VARCHAR(30) NOT NULL,
    chicken_grams NUMERIC(10,1),
    rice_grams NUMERIC(10,1),
    rice_type VARCHAR(40),
    equipment VARCHAR(40),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cooking_setup_user UNIQUE (user_id)
);

ALTER TABLE cooking_session ADD COLUMN rice_type VARCHAR(40);
ALTER TABLE cooking_session ADD COLUMN equipment VARCHAR(40);
