-- The account that authenticated the session remains user_id.  active_user_id is
-- an optional, isolated profile selected by that account (currently used for demos).
ALTER TABLE web_session
    ADD COLUMN active_user_id BIGINT REFERENCES app_user(id);

CREATE INDEX idx_web_session_active_user
    ON web_session(active_user_id)
    WHERE active_user_id IS NOT NULL;
