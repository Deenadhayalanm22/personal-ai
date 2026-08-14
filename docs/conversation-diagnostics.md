# Conversation diagnostics

`conversation_diagnostic_turn` is temporary MVP-quality data, not a source of business truth. It records
each accepted customer message together with the system reply, resulting conversation state, and any
entity exposed by `SpeechResult.savedEntity`.

Daily review query:

```sql
SELECT id, created_at, external_user_id, input_kind, input_text,
       response_status, response_text, response_media_type, response_media_filename,
       response_media_size, active_intent, waiting_for_field,
       partial_json, saved_entity_type, saved_entity_json, review_notes
FROM conversation_diagnostic_turn
WHERE reviewed = FALSE
ORDER BY created_at, id;
```

After reviewing a turn:

```sql
UPDATE conversation_diagnostic_turn
SET reviewed = TRUE,
    review_notes = 'Expected account to resolve to HDFC bank account'
WHERE id = 123;
```

Export unreviewed turns for analysis:

```sql
\copy (SELECT * FROM conversation_diagnostic_turn WHERE reviewed = FALSE ORDER BY created_at, id) TO 'conversation-diagnostics.csv' CSV HEADER
```

Delete old reviewed diagnostics regularly because messages may contain personal financial information:

```sql
DELETE FROM conversation_diagnostic_turn
WHERE reviewed = TRUE
  AND created_at < now() - interval '30 days';
```
