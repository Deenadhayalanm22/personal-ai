-- Legacy confirmed transactions predate mandatory taxonomy spending nature.
-- This exact taxonomy pair has a deterministic ESSENTIAL classification.
WITH repaired AS (
    UPDATE financial_transaction
       SET spending_nature = 'ESSENTIAL', updated_at = now()
     WHERE deleted_at IS NULL
       AND spending_nature IS NULL
       AND category = 'Food & Dining'
       AND subcategory = 'Meat, Fish & Eggs'
    RETURNING user_id
)
UPDATE money_story_snapshot
   SET status = 'STALE'
 WHERE superseded_at IS NULL
   AND user_id IN (SELECT user_id FROM repaired);
