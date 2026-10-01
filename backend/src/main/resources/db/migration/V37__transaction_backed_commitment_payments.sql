-- Plans stay separate; actual recurring payments share the expense read model.
ALTER TABLE financial_transaction ALTER COLUMN source_draft_id DROP NOT NULL;
ALTER TABLE financial_transaction
    ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'CAPTURE',
    ADD COLUMN payment_reference VARCHAR(100) UNIQUE,
    ADD COLUMN description VARCHAR(255),
    ADD CONSTRAINT ck_financial_transaction_origin CHECK (
        (origin = 'CAPTURE' AND source_draft_id IS NOT NULL)
        OR (origin = 'COMMITMENT_PAYMENT' AND source_draft_id IS NULL AND payment_reference IS NOT NULL));
ALTER TABLE recurring_commitment_occurrence
    ADD COLUMN payment_transaction_id BIGINT UNIQUE REFERENCES financial_transaction(id);
ALTER TABLE recurring_commitment_extra
    ADD COLUMN payment_transaction_id BIGINT UNIQUE REFERENCES financial_transaction(id),
    ADD COLUMN request_id VARCHAR(100),
    ADD CONSTRAINT uq_commitment_extra_request UNIQUE (occurrence_id, request_id);

-- Do not invent amounts for old acknowledgement-only completions.
-- A previously explicitly matched expense is reused only when the commitment,
-- owner, amount and payment date agree and there is exactly one candidate.
-- Ambiguity stops the upgrade atomically for explicit reconciliation.
DO $$
DECLARE p RECORD; candidate_count INTEGER; payment_id BIGINT; e RECORD;
BEGIN
    FOR p IN SELECT o.*, c.user_id, c.label, c.category, c.subcategory, c.merchant_id
        FROM recurring_commitment_occurrence o JOIN user_recurring_commitment c ON c.id = o.commitment_id
        WHERE o.status = 'COMPLETED' AND o.actual_amount > 0 AND o.completed_at IS NOT NULL
        ORDER BY o.id
    LOOP
        SELECT COUNT(*), MIN(t.id) INTO candidate_count, payment_id
        FROM financial_transaction t WHERE t.user_id = p.user_id AND t.deleted_at IS NULL
            AND t.recurring_commitment_id = p.commitment_id AND t.commitment_match_status = 'MATCHED'
            AND t.occurred_at = p.completed_at AND t.amount = p.actual_amount;
        IF candidate_count > 1 OR EXISTS (SELECT 1 FROM recurring_commitment_occurrence WHERE payment_transaction_id = payment_id) THEN
            RAISE EXCEPTION 'Reconcile commitment occurrence % before upgrading: ambiguous existing payment', p.id;
        END IF;
        IF candidate_count = 0 THEN
            INSERT INTO financial_transaction(user_id, amount, occurred_at, category, subcategory, merchant_id,
                recurring_commitment_id, commitment_match_status, origin, payment_reference, description, created_at, updated_at)
            VALUES (p.user_id, p.actual_amount, p.completed_at, p.category, p.subcategory, p.merchant_id,
                p.commitment_id, 'MATCHED', 'COMMITMENT_PAYMENT', 'occurrence:' || p.id, p.label, p.created_at, p.updated_at)
            RETURNING id INTO payment_id;
        END IF;
        UPDATE financial_transaction SET payment_reference = 'occurrence:' || p.id WHERE id = payment_id;
        UPDATE recurring_commitment_occurrence SET payment_transaction_id = payment_id WHERE id = p.id;
    END LOOP;
    -- An aggregate extra without a dated extra row has no reliable payment date.
    -- Stop rather than place several historical payments on an invented date.
    IF EXISTS (
        SELECT 1 FROM recurring_commitment_occurrence o
        LEFT JOIN recurring_commitment_extra legacy_extra ON legacy_extra.occurrence_id = o.id
        WHERE o.status = 'COMPLETED' GROUP BY o.id
        HAVING o.extra_amount > COALESCE(SUM(legacy_extra.amount), 0)
    ) THEN
        RAISE EXCEPTION 'Reconcile undated legacy commitment extras before upgrading';
    END IF;
    FOR e IN SELECT x.*, c.user_id, c.id AS commitment_id, c.label, c.category, c.subcategory, c.merchant_id
        FROM recurring_commitment_extra x JOIN recurring_commitment_occurrence o ON o.id = x.occurrence_id
        JOIN user_recurring_commitment c ON c.id = o.commitment_id WHERE o.status = 'COMPLETED'
    LOOP
        SELECT COUNT(*), MIN(t.id) INTO candidate_count, payment_id FROM financial_transaction t
        WHERE t.user_id = e.user_id AND t.deleted_at IS NULL AND t.recurring_commitment_id = e.commitment_id
            AND t.commitment_match_status = 'MATCHED' AND t.amount = e.amount AND t.payment_reference IS NULL
            AND t.occurred_at = (e.created_at AT TIME ZONE (SELECT timezone FROM app_user WHERE id = e.user_id))::date;
        IF candidate_count > 1 THEN
            RAISE EXCEPTION 'Reconcile commitment extra % before upgrading: ambiguous existing payment', e.id;
        END IF;
        IF candidate_count = 0 THEN
            INSERT INTO financial_transaction(user_id, amount, occurred_at, category, subcategory, merchant_id,
                recurring_commitment_id, commitment_match_status, origin, payment_reference, description, created_at, updated_at)
            VALUES(e.user_id, e.amount, (e.created_at AT TIME ZONE (SELECT timezone FROM app_user WHERE id = e.user_id))::date,
                e.category, e.subcategory, e.merchant_id, e.commitment_id, 'MATCHED', 'COMMITMENT_PAYMENT',
                'extra:' || e.id, LEFT(e.label || ' · ' || e.reason, 255), e.created_at, e.created_at) RETURNING id INTO payment_id;
        END IF;
        UPDATE financial_transaction SET payment_reference = 'extra:' || e.id WHERE id = payment_id;
        UPDATE recurring_commitment_extra SET payment_transaction_id = payment_id WHERE id = e.id;
    END LOOP;
END $$;
-- Rebuild historical aggregates after the backfill as well as current ones.
INSERT INTO expense_aggregate_dirty_date(aggregate_date)
SELECT DISTINCT occurred_at FROM financial_transaction WHERE origin = 'COMMITMENT_PAYMENT'
ON CONFLICT DO NOTHING;
