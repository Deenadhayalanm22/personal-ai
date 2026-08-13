INSERT INTO user_feature_flag (channel, external_user_id, role, enabled)
VALUES
    ('WHATSAPP', '919876543210', 'SUPER_ADMIN', TRUE),
    ('WHATSAPP', '919876543299', 'USER', TRUE),
    ('WHATSAPP', '919876543298', 'USER', TRUE),
    ('WHATSAPP', '919876543297', 'USER', TRUE),
    ('WHATSAPP', '919876543296', 'USER', TRUE)
ON CONFLICT (channel, external_user_id)
DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now();
