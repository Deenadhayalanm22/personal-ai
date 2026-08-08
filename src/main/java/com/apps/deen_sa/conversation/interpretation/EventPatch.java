package com.apps.deen_sa.conversation.interpretation;

import java.util.List;

public record EventPatch(
        String eventId,
        String eventType,
        EventFields fields,
        List<String> unresolvedFields,
        List<String> ambiguities,
        List<FieldEvidence> evidence
) implements com.apps.deen_sa.extension.api.ExtensionEvent {
    public EventPatch {
        unresolvedFields = unresolvedFields == null ? List.of() : List.copyOf(unresolvedFields);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        fields = (fields == null ? new EventFields(java.util.Map.of()) : fields)
                .sanitized(evidence);
    }

    public EventPatch(String eventId, String eventType, java.util.Map<String, Object> fields,
                      List<String> unresolvedFields, List<String> ambiguities, List<FieldEvidence> evidence) {
        this(eventId, eventType, EventFields.from(fields), unresolvedFields, ambiguities, evidence);
    }

    @Override public java.util.Map<String, Object> facts() { return fields.asMap(); }
}
