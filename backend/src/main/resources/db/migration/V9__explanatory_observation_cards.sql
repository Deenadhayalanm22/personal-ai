ALTER TABLE money_story ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE money_story_evidence ADD COLUMN subcategory_label VARCHAR(100);
-- Refresh old templates through the normal worker; old evidence is retained as historical snapshots.
UPDATE money_story_snapshot SET status = 'STALE' WHERE superseded_at IS NULL;
