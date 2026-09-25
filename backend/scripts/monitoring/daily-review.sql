-- PostgreSQL. One read-only SELECT; default: yesterday in Asia/Kolkata.
-- Replace the report_date expression with DATE '2026-09-24' to select a day.
-- Excludes direct account identifiers; free text and story payloads ARE sensitive.
-- This is current state for a daily activity cohort, NOT an end-of-day audit log.
WITH
params AS (
    SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')::date - 1 AS report_date,
           'Asia/Kolkata'::text AS report_timezone
),
bounds AS (
    SELECT p.*, p.report_date::timestamp AT TIME ZONE p.report_timezone AS start_at,
           (p.report_date + 1)::timestamp AT TIME ZONE p.report_timezone AS end_at
    FROM params p
),
capture AS (
    SELECT d.*, to_jsonb(e) AS extraction,
           CASE WHEN t.id IS NOT NULL THEN to_jsonb(t) || jsonb_build_object(
               'merchant_label', m.canonical_name,
               'source_account_label', a.canonical_name) END AS recorded_expense,
           array_remove(ARRAY[
               CASE WHEN d.status = 'CONSUMED' AND t.id IS NULL
                    THEN 'CHECK_CONSUMED_WITHOUT_TRANSACTION' END,
               CASE WHEN t.id IS NOT NULL AND
                         (d.status <> 'CONSUMED' OR e.status IS DISTINCT FROM 'USED')
                    THEN 'CHECK_TRANSACTION_CONFIRMATION_STATE' END,
               CASE WHEN t.user_id <> d.user_id
                    THEN 'CHECK_TRANSACTION_OWNER_MISMATCH' END,
               CASE WHEN t.deleted_at IS NULL AND t.id IS NOT NULL AND
                         (nullif(trim(t.category), '') IS NULL OR
                          nullif(trim(t.subcategory), '') IS NULL OR t.spending_nature IS NULL)
                    THEN 'CHECK_INCOMPLETE_CLASSIFICATION' END,
               CASE WHEN d.status IN ('PENDING', 'TRANSCRIPT_REVIEW')
                         AND d.updated_at < CURRENT_TIMESTAMP - INTERVAL '24 hours'
                    THEN 'REVIEW_WAITING_OVER_24H_MAY_BE_USER_INACTION' END,
               CASE WHEN d.status = 'PENDING' AND e.id IS NULL
                    THEN 'REVIEW_NO_EXTRACTION_MAY_BE_NON_EXPENSE_OR_PROCESSING_FAILURE' END,
               CASE WHEN d.status = 'PENDING' AND e.status = 'ACTIVE' AND
                         (e.amount IS NULL OR e.amount <= 0 OR
                          nullif(trim(e.category_id), '') IS NULL OR
                          nullif(trim(e.subcategory_id), '') IS NULL)
                    THEN 'CHECK_PENDING_EXTRACTION_CANNOT_BE_CONFIRMED' END,
               CASE WHEN e.confidence < 0.70 THEN 'REVIEW_LOW_CONFIDENCE_HEURISTIC' END,
               CASE WHEN t.id IS NOT NULL AND
                         (t.amount IS DISTINCT FROM e.amount OR
                          t.category IS DISTINCT FROM e.category_id OR
                          t.subcategory IS DISTINCT FROM e.subcategory_id OR
                          t.occurred_at IS DISTINCT FROM e.occurred_at)
                    THEN 'REVIEW_CHANGED_SINCE_EXTRACTION_MAY_BE_PORTAL_EDIT' END
           ], NULL) AS review_flags
    FROM transaction_draft d
    CROSS JOIN bounds b
    LEFT JOIN transaction_draft_extraction e ON e.draft_id = d.id
    LEFT JOIN financial_transaction t ON t.source_draft_id = d.id
    LEFT JOIN user_reference_entity m ON m.id = t.merchant_id
    LEFT JOIN user_reference_entity a ON a.id = t.source_account_id
    WHERE (d.created_at >= b.start_at AND d.created_at < b.end_at)
       OR (d.updated_at >= b.start_at AND d.updated_at < b.end_at)
       OR (t.created_at >= b.start_at AND t.created_at < b.end_at)
       OR (t.updated_at >= b.start_at AND t.updated_at < b.end_at)
       OR (t.deleted_at >= b.start_at AND t.deleted_at < b.end_at)
       OR (d.status IN ('PENDING', 'TRANSCRIPT_REVIEW') AND d.created_at < b.end_at)
),
selected_snapshots AS (
    SELECT s.* FROM money_story_snapshot s CROSS JOIN bounds b
    WHERE (s.generated_at >= b.start_at AND s.generated_at < b.end_at)
       OR (s.superseded_at IS NULL AND
           (s.status <> 'READY' OR s.user_id IN (SELECT user_id FROM capture)))
),
report_rows AS (
    SELECT 0 AS sort_order, 'report'::text AS section, NULL::bigint AS user_id,
           'daily-review-v1'::text AS record_id,
           jsonb_build_object(
               'report_date', b.report_date, 'timezone', b.report_timezone,
               'start_inclusive', b.start_at, 'end_exclusive', b.end_at,
               'exported_at', CURRENT_TIMESTAMP,
               'capture_rows_including_pending_backlog', (SELECT count(*) FROM capture),
               'new_drafts', (SELECT count(*) FROM transaction_draft d
                             WHERE d.created_at >= b.start_at AND d.created_at < b.end_at),
               'new_transactions_including_later_deleted', (SELECT count(*) FROM financial_transaction t
                             WHERE t.created_at >= b.start_at AND t.created_at < b.end_at),
               'selected_snapshots', (SELECT count(*) FROM selected_snapshots),
               'notes', 'Current database state, not historical end-of-day state. Flags require investigation. Empty stories may be valid. Live MONTHLY_COMMITMENT copy, outbound replies, audio files and runtime failures are not included.'
           ) AS detail
    FROM bounds b
    UNION ALL
    SELECT 1, 'capture', c.user_id, c.id::text,
           jsonb_build_object(
               'draft_id', c.id, 'input_type', c.input_type, 'source', c.source,
               'raw_text', c.raw_text, 'transcribed_text', c.transcribed_text,
               'normalized_text', c.normalized_text, 'draft_status', c.status,
               'created_at', c.created_at, 'updated_at', c.updated_at,
               'user_timezone', u.timezone, 'currency', u.currency,
               'pending_action_context_id', c.pending_action_context_id,
               'extraction', c.extraction, 'recorded_expense', c.recorded_expense,
               'review_flags', to_jsonb(c.review_flags))
    FROM capture c JOIN app_user u ON u.id = c.user_id
    UNION ALL
    SELECT 2, 'story_snapshot', s.user_id, s.id::text,
           to_jsonb(s) || jsonb_build_object(
               'is_current_at_export', s.superseded_at IS NULL,
               'story_count', (SELECT count(*) FROM money_story st WHERE st.snapshot_id = s.id))
    FROM selected_snapshots s
    UNION ALL
    SELECT 3, 'story', s.user_id, st.id::text,
           to_jsonb(st) || jsonb_build_object(
               'snapshot_status', s.status, 'scope_month', s.scope_month,
               'is_current_at_export', s.superseded_at IS NULL,
               'evidence', COALESCE((
                   SELECT jsonb_agg(to_jsonb(e) || jsonb_build_object(
                       'current_transaction', to_jsonb(t),
                       'source_draft_text', d.raw_text,
                       'source_transcript', d.transcribed_text,
                       'owner_mismatch', t.user_id <> s.user_id,
                       'transaction_changed_since_generation',
                           t.updated_at > s.generated_at OR t.deleted_at IS NOT NULL
                   ) ORDER BY e.ordinal)
                   FROM money_story_evidence e
                   JOIN financial_transaction t ON t.id = e.transaction_id
                   JOIN transaction_draft d ON d.id = t.source_draft_id
                   WHERE e.story_id = st.id
               ), '[]'::jsonb))
    FROM selected_snapshots s JOIN money_story st ON st.snapshot_id = s.id
    UNION ALL
    SELECT 4, 'planning_snapshot_not_live_story', s.user_id, s.id::text, to_jsonb(s)
    FROM monthly_financial_snapshot s CROSS JOIN bounds b
    WHERE (s.updated_at >= b.start_at AND s.updated_at < b.end_at)
       OR (s.user_id IN (SELECT user_id FROM capture) AND
           s.scope_month >= date_trunc('month', b.report_date)::date AND
           s.scope_month < (date_trunc('month', b.report_date) + INTERVAL '2 months')::date)
)
SELECT section, user_id, record_id, detail
FROM report_rows
ORDER BY sort_order, user_id NULLS FIRST, record_id;
