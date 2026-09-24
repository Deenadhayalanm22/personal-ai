# Platform and WhatsApp

## Health

`GET /health` is public and returns plain text `OK`. `App.svelte` uses it as a bounded startup/reconnectivity check before authenticated dashboard loads. `VITE_HEALTH_PATH` and `VITE_HEALTH_TIMEOUT_MS` configure client behavior. It is also suitable for deployment health checks.

## WhatsApp webhook

This is a Meta inbound integration, not a browser API; no frontend component calls it.

| Endpoint | Contract |
| --- | --- |
| `GET /webhook/whatsapp` | Requires `hub.mode`, `hub.verify_token`, `hub.challenge`. Returns raw challenge only for `subscribe` with matching `WHATSAPP_VERIFY_TOKEN`; `403` on mismatch, `503` if unconfigured. |
| `POST /webhook/whatsapp` | Accepts `entry[].changes[].value.messages[]`. Messages may include `text.body`, `audio.id`/`audio.mime_type`, or `interactive.button_reply`/`interactive.list_reply`. Returns `200` after synchronous ingestion. |

Ingestion records interactive confirmation commands first. It then handles supported aggregate-backfill administration commands before routing remaining text/audio into drafts, normalization, and outbound WhatsApp reply/confirmation.
Audio messages are stored as drafts and downloaded from Meta using the configured WhatsApp token. The configured OpenAI transcription model returns recognized words, which are saved in `transaction_draft.transcribed_text` and sent in an interactive `Confirm words` / `Discard` reply. `v2:audio:confirm:{draftId}` sends the approved words through expense extraction, which sends the usual separate expense confirmation. `v2:audio:discard:{draftId}` cancels the staged draft. Review replies are owner-scoped and repeated replies do not record an expense.
Expense amounts in the transcript review are displayed as digits without currency symbols or names. The normalized review wording is what the user approves and what the expense extractor receives.
Audio transcripts retain the speaker's language in `transcribed_text`, the review reply, and the text passed to expense extraction. The extractor maps category and subcategory to configured English taxonomy labels; merchant and account names remain recognizable and are transliterated to Latin script when needed.
The first newly routed text or audio message of each day in `app.aggregation.time-zone` queues background work to backfill missing aggregates through yesterday, refresh yesterday, and evaluate daily actions. The webhook does not wait for this work; a failed run can be retried by a subsequent message.

All public browser API errors use `{ "code": "MACHINE_READABLE_CODE", "message": "Human-readable message" }`. Bad/missing typed parameters return `400`; expired or invalid sessions return `401 UNAUTHORIZED`.
