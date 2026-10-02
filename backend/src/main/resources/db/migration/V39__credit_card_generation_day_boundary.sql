-- V38 used the generation date as an inclusive purchase cutoff. The corrected
-- cycle ends the previous day. Preserve saved amounts, due months and payment dates.
UPDATE credit_card_bill_payment SET statement_end = statement_end - 1;
