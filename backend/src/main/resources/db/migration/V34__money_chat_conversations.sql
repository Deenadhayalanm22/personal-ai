CREATE TABLE money_chat_conversation (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    month CHAR(7) NOT NULL,
    draft VARCHAR(2000) NOT NULL DEFAULT '',
    messages JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, id),
    CONSTRAINT money_chat_month_format CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);

CREATE INDEX money_chat_conversation_recent ON money_chat_conversation (user_id, updated_at DESC);
