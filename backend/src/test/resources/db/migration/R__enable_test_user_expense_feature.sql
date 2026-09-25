INSERT INTO app_user (channel, external_user_id, role, portal_enabled)
VALUES
    ('WHATSAPP', '919876543210', 'SUPER_ADMIN', TRUE),
    ('WHATSAPP', '919876543299', 'USER', TRUE),
    ('WHATSAPP', '919876543298', 'USER', TRUE),
    ('WHATSAPP', '919876543297', 'USER', TRUE),
    ('WHATSAPP', '919876543296', 'USER', TRUE)
ON CONFLICT (channel, external_user_id)
DO UPDATE SET portal_enabled = EXCLUDED.portal_enabled, role = EXCLUDED.role;
