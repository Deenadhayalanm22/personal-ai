# Reference merge API

All endpoints require the `WEB_SESSION` cookie and operate only on the authenticated user's data.

## List mergeable references

`GET /api/web/reference-preferences`

The existing preference response now includes `transactionCount`, the number of transactions currently linked through the corresponding merchant or source-account relationship. Beneficiary counts are currently zero because transactions do not yet have a beneficiary relationship.

```json
{
  "references": [{
    "referenceId": 101,
    "primaryReference": "HDFC Bank account",
    "entityType": "ACCOUNT",
    "transactionCount": 9,
    "aliases": [{"aliasId": 1, "alias": "HDFC BANK A/C"}]
  }]
}
```

## Merge references

`POST /api/web/reference-preferences/merge`

```json
{
  "entityType": "ACCOUNT",
  "referenceIds": [101, 214],
  "canonicalName": "HDFC Bank"
}
```

At least two unique IDs are required. All references must be active, unmerged, owned by the user, and match `entityType`. Names are trimmed and limited to 255 characters. An existing active reference with the requested name is reused. The deterministic request key makes an identical completed request idempotent.

The operation atomically moves transaction relationships to the canonical reference, copies missing names and aliases to it, and marks source references inactive. The returned merge ID is deterministic for the normalized request; no separate merge-audit tables are created.

API errors use the standard response shape:

```json
{"code":"MIXED_ENTITY_TYPES","message":"All references must have the requested entity type"}
```
