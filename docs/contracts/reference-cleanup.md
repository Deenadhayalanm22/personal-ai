# Reference cleanup

Reference cleanup standardizes conversationally captured merchant, beneficiary, and account names. It changes aliases and, for merchant/account merges, repoints related historical transactions. `frontend/src/Normalization.svelte` owns this workflow.

| Endpoint | Request and response | Frontend owner |
| --- | --- | --- |
| `GET /api/web/reference-entity-types` | `{ entityTypes: ["MERCHANT", "BENEFICIARY", "ACCOUNT"] }`. | Normalization load; cached per active profile in `api.js`. |
| `GET /api/web/reference-preferences` | `{ references }`; item fields: `referenceId`, `entityType`, `primaryReference`, `aliases`, `transactionCount`. Alias: `{ aliasId, alias }`. | List and merge picker. |
| `POST /api/web/reference-preferences` | Body `{ entityType, primaryReference, alias }`; returns preference with `201`. | Add-name dialog. |
| `POST /api/web/reference-preferences/merge` | Body `{ entityType, referenceIds: [1, 2], canonicalName: "Preferred name" }`; at least two distinct active owned references of one type. Returns `{ mergeId, canonicalReference, mergedReferenceIds, updatedTransactionCount, status: "COMPLETED" }`. | Merge dialog, then reloads list. |

Merge selects an existing active reference with the requested canonical name when possible, otherwise a selected reference. It preserves names and aliases, deactivates other selected references, and repoints expense records for `MERCHANT` and `ACCOUNT`. `BENEFICIARY` has no expense link today. Key failures: `400 INVALID_REFERENCE_MERGE`, `404 REFERENCE_NOT_FOUND`, `403 REFERENCE_FORBIDDEN`, `409 REFERENCE_NOT_ACTIVE`, `422 MIXED_ENTITY_TYPES`.

The workflow is reachable through **You → Manage names** in the frontend, and the existing V1 transaction cleanup shortcut. Capture preparation reads owned active names/aliases without creating names; confirmation resolves active aliases again, including a name merged after preview. Beneficiary cleanup remains alias-only.

Account merges touching any configured bank or credit-card reference (including the named canonical target) return `409 CONFIGURED_ACCOUNT_MERGE`; keep these identities and add aliases instead. Unconfigured account merges remain supported.
