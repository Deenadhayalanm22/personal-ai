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

All public browser API errors use `{ "code": "MACHINE_READABLE_CODE", "message": "Human-readable message" }`. Bad/missing typed parameters return `400`; expired or invalid sessions return `401 UNAUTHORIZED`.
