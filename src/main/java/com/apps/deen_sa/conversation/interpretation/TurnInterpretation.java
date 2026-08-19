package com.apps.deen_sa.conversation.interpretation;

import java.util.List;

public record TurnInterpretation(
        TurnType turnType,
        String intent,
        String language,
        String targetEventId,
        List<EventPatch> events,
        String command,
        QueryPeriod query,
        String analysisIntent,
        String presentationMood,
        List<String> ambiguities,
        Double confidence
) {
    public TurnInterpretation {
        events = events == null ? List.of() : List.copyOf(events);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
    }

    /** Source-compatible constructor for event turns and existing extension tests. */
    public TurnInterpretation(TurnType turnType, String intent, String language, String targetEventId,
                              List<EventPatch> events, String command, QueryPeriod query,
                              List<String> ambiguities, Double confidence) {
        this(turnType, intent, language, targetEventId, events, command, query,
                null, null, ambiguities, confidence);
    }
}
