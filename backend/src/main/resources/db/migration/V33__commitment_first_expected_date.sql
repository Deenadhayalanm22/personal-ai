ALTER TABLE user_recurring_commitment ADD COLUMN first_expected_date DATE;

UPDATE user_recurring_commitment
SET first_expected_date = next_expected_date
WHERE recurrence_unit IN ('DAY', 'WEEK');
