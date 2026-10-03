-- User-provided first due month; existing cards retain their historical coverage.
ALTER TABLE user_credit_card ADD COLUMN start_month DATE;
ALTER TABLE user_credit_card ADD CONSTRAINT ck_credit_card_start_month CHECK (start_month IS NULL OR EXTRACT(DAY FROM start_month) = 1);
