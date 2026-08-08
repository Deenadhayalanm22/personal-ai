ALTER TABLE conversation_session ADD COLUMN IF NOT EXISTS pending_events_json JSONB;
ALTER TABLE conversation_session ADD COLUMN IF NOT EXISTS recent_turns_json JSONB;
ALTER TABLE conversation_session ADD COLUMN IF NOT EXISTS last_question TEXT;
ALTER TABLE conversation_session ADD COLUMN IF NOT EXISTS interpreter_version VARCHAR(50);
