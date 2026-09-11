ALTER TABLE money_story ADD COLUMN story_level VARCHAR(20);

-- Newer snapshots already carry the level in their payload. Pre-ladder stories were
-- generated only by the original pattern rules; preserve their historical classification.
UPDATE money_story
SET story_level = COALESCE(payload::jsonb ->> 'level', 'PATTERN');

-- Keep legacy API payloads consistent with the explicit persisted classification.
UPDATE money_story
SET payload = jsonb_set(payload::jsonb, '{level}', to_jsonb(story_level))::text
WHERE payload::jsonb ->> 'level' IS NULL;

ALTER TABLE money_story ALTER COLUMN story_level SET NOT NULL;
ALTER TABLE money_story ADD CONSTRAINT ck_money_story_level
    CHECK (story_level IN ('OBSERVATION', 'PATTERN'));
