DROP TABLE IF EXISTS taxonomy_candidate_occurrence;
DROP TABLE IF EXISTS taxonomy_candidate;
ALTER TABLE conversation_session DROP COLUMN IF EXISTS active_draft_id;
DROP TABLE IF EXISTS expense_draft;
DROP TABLE IF EXISTS transaction_tag;
DROP TABLE IF EXISTS tag;
DROP TABLE IF EXISTS state_mutation;
DROP TABLE IF EXISTS state_change;
