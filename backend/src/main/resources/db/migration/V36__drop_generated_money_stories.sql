-- Generated spending stories are retired. Monthly commitments use monthly_financial_snapshot.
-- The CASCADE relationships from evidence to story to snapshot make row deletion explicit.
DROP TABLE IF EXISTS money_story_evidence;
DROP TABLE IF EXISTS money_story;
DROP TABLE IF EXISTS money_story_snapshot;
