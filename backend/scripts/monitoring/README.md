# Daily production review

Run `daily-review.sql` in your PostgreSQL client and export the entire result as CSV.
It is one SELECT with no writes. Use a read-only database login, ideally on a replica.
The default window is yesterday, midnight to midnight in `Asia/Kolkata`, independent
of the database session timezone. Change `report_date` in the first CTE for another day.

From the repository root, with `DATABASE_URL` already configured securely:

```sh
PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=60000' \
psql "$DATABASE_URL" -X --set=ON_ERROR_STOP=1 --csv \
  --file=backend/scripts/monitoring/daily-review.sql > daily-review.csv
```

The query targets the schema in this repository through V29. It does not migrate
production. Large histories can make the result sizable; the 60-second timeout in
the command prevents an unexpectedly long run. There is no silent row truncation.

## What to share

Share the exported CSV after reviewing its contents. Internal user IDs allow
comparisons across days without exporting `external_user_id`, session tokens or
WhatsApp message IDs. This is **not anonymized**: messages, transcripts, merchant
and account labels, evidence and planning payloads can contain personal details.
Remove identifying text or any information your friends have not agreed to share.
Preserve row IDs, dates, amounts and classification where possible for diagnosis.

## Reading the report

- `report`: date bounds, export time and counts; present even with no activity.
- `capture`: drafts created/updated that day, drafts whose expense changed that
  day, and unresolved drafts from before the window end. Shows original text,
  transcript, extraction and current expense side by side. `normalized_text` may
  be null; the extraction is the persisted structured result.
- `story_snapshot`: snapshots generated that day, all current snapshots for
  capture users, and all current non-READY snapshots. Historical/superseded rows
  are explicitly marked. A READY snapshot with zero stories can be valid.
- `story`: stored card payload and ordered evidence with current transactions and
  original source words. `payload` is JSON stored as a string. Evidence can
  legitimately differ after an edit or deletion, especially on old snapshots.
- `planning_snapshot_not_live_story`: daily changed planning projections and
  current/next report-month projections for capture users. These are inputs,
  not the live monthly commitment card or its generated copy.

Flags are triage hints, not proof of bugs. `CHECK_PENDING_EXTRACTION_CANNOT_BE_CONFIRMED`
identifies a legacy active extraction missing facts required by the confirmation
writer. Waiting more than 24 hours can mean the
user has not confirmed; confidence below 0.70 is a monitoring heuristic, not an
application acceptance threshold. Extraction/final differences can be intentional
portal corrections. Check category meaning against the original words, verify
amount/date, inspect unexpected cancellations, and compare story claims with their
period and evidence. The query does not validate the complete taxonomy or
recalculate every story rule.

## Limits

This reports current persisted state for a daily activity cohort. Re-running an
old date may produce different results after edits, confirmations or regeneration;
save each export if you want to compare days. Some extraction status changes have
no separate update timestamp. This is not a complete conversation/audit log.
Do not treat capture-row counts (which include backlog) as that day's new messages.

SQL cannot establish whether the audio transcription matches the audio, show all
outbound WhatsApp replies/delivery failures, find requests that failed before a
draft was stored, reconstruct overwritten values, measure model latency/cost, or
verify frontend rendering. The live MONTHLY_COMMITMENT story is assembled on API
read, so its exact copy needs an API response or screenshot. No missing-snapshot
alert is asserted because eligibility and generation timing need application
context. App logs may be needed to explain any suspicious rows.
