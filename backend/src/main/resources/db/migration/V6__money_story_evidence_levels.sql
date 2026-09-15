-- Nullable for legacy snapshots; the next evaluation upgrades them.
ALTER TABLE money_story_snapshot ADD COLUMN evaluated_on DATE;
ALTER TABLE money_story_snapshot ADD COLUMN content_fingerprint VARCHAR(64);
