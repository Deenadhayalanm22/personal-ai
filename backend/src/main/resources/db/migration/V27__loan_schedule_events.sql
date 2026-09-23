ALTER TABLE user_loan ADD COLUMN restructured_from DATE;
ALTER TABLE loan_emi_occurrence ADD COLUMN bank_penalty_amount NUMERIC(19,2);
ALTER TABLE loan_emi_occurrence ADD COLUMN pre_closure_settlement BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE loan_emi_occurrence ADD COLUMN planned_amount NUMERIC(19,2);
