ALTER TABLE user_recurring_commitment
    ADD COLUMN recurrence_unit VARCHAR(12) NOT NULL DEFAULT 'MONTH',
    ADD COLUMN recurrence_interval INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN next_expected_date DATE,
    ADD COLUMN flexible_schedule BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_recurring_commitment_interval CHECK (recurrence_interval > 0);

UPDATE user_recurring_commitment
SET next_expected_date = effective_month + (COALESCE(due_day, 1) - 1) * INTERVAL '1 day'
WHERE next_expected_date IS NULL;

ALTER TABLE recurring_commitment_occurrence
    ADD COLUMN actual_amount NUMERIC(19,2);
