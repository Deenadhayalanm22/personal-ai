-- Retain old and new expense dates across edits/backfills until the worker rebuilds them.
CREATE TABLE expense_aggregate_dirty_date (
    aggregate_date DATE PRIMARY KEY
);
