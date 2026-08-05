package com.apps.deen_sa.conversation.interpretation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PendingEvent(
        String eventId,
        String eventType,
        Long persistedEntityId,
        Map<String, Object> knownFacts,
        List<String> unresolvedFields,
        List<String> ambiguities,
        List<FieldEvidence> evidence
) {
    public PendingEvent {
        knownFacts = knownFacts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(knownFacts);
        unresolvedFields = unresolvedFields == null ? new ArrayList<>() : new ArrayList<>(unresolvedFields);
        ambiguities = ambiguities == null ? new ArrayList<>() : new ArrayList<>(ambiguities);
        evidence = evidence == null ? new ArrayList<>() : new ArrayList<>(evidence);
    }
}
