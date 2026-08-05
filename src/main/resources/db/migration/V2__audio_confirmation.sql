CREATE TABLE audio_confirmation (
    id UUID PRIMARY KEY,
    whatsapp_user_id VARCHAR(255) NOT NULL,
    media_id VARCHAR(255) NOT NULL,
    transcribed_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audio_confirmation_user_status
    ON audio_confirmation (whatsapp_user_id, status);
