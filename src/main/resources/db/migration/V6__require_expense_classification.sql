-- Existing incomplete expenses cannot be clarified retroactively. Preserve them
-- under the taxonomy's explicit fallback pair before enforcing the invariant.
UPDATE state_change
SET category = 'Miscellaneous', subcategory = 'Others'
WHERE transaction_type = 'EXPENSE'
  AND (category IS NULL OR BTRIM(category) = ''
       OR subcategory IS NULL OR BTRIM(subcategory) = '');

ALTER TABLE state_change
    ADD CONSTRAINT chk_expense_has_classification
    CHECK (transaction_type <> 'EXPENSE'
        OR (category IS NOT NULL AND BTRIM(category) <> ''
            AND subcategory IS NOT NULL AND BTRIM(subcategory) <> ''));
