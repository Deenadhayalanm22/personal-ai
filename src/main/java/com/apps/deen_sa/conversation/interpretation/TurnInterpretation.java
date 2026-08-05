package com.apps.deen_sa.conversation.interpretation;

import java.util.List;

public record TurnInterpretation(
        TurnType turnType,
        String intent,
        String targetEventId,
        List<EventPatch> events,
        String command,
        String query,
        List<String> ambiguities,
        Double confidence
) {
    public TurnInterpretation {
        events = events == null ? List.of() : List.copyOf(events);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
    }
}
