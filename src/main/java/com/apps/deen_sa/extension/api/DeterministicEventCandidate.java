package com.apps.deen_sa.extension.api;

import java.util.Map;

/** An event whose type and facts were extracted mechanically by its owning extension. */
public record DeterministicEventCandidate(String eventType, Map<String, Object> fields) {
    public DeterministicEventCandidate {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
