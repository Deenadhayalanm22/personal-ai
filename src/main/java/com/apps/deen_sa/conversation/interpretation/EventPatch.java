package com.apps.deen_sa.conversation.interpretation;

import java.util.List;

public record EventPatch(
        String eventId,
        String eventType,
        EventFields fields,
        List<String> unresolvedFields,
        List<String> ambiguities,
        List<FieldEvidence> evidence
) {
    public EventPatch {
        fields = fields == null ? new EventFields(null, null, null, null, null, null, null, null, null) : fields;
        unresolvedFields = unresolvedFields == null ? List.of() : List.copyOf(unresolvedFields);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public EventPatch(String eventId, String eventType, java.util.Map<String, Object> fields,
                      List<String> unresolvedFields, List<String> ambiguities, List<FieldEvidence> evidence) {
        this(eventId, eventType, EventFields.from(fields), unresolvedFields, ambiguities, evidence);
    }
}
