ALTER TABLE app_user
    ADD COLUMN portal_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'SUPER_ADMIN'));

-- Existing WhatsApp senders may already have an app_user row. Preserve its ID and data.
INSERT INTO app_user (channel, external_user_id, portal_enabled, role)
SELECT channel, external_user_id, enabled, role
FROM user_feature_flag
ON CONFLICT (channel, external_user_id)
DO UPDATE SET portal_enabled = EXCLUDED.portal_enabled, role = EXCLUDED.role;

DROP TABLE user_feature_flag;
