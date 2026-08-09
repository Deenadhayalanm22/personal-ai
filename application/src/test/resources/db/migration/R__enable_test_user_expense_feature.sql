INSERT INTO user_feature_flag (channel, external_user_id, feature_key, enabled)
VALUES
    ('WHATSAPP', '919876543210', 'EXPENSE', TRUE),
    ('WHATSAPP', '919876543299', 'EXPENSE', TRUE)
ON CONFLICT (channel, external_user_id, feature_key)
DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now();
