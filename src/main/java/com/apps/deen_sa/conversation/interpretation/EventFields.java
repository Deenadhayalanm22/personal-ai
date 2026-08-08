package com.apps.deen_sa.conversation.interpretation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.LocalDate;
import java.util.*;

/** Generic, evidence-sanitized extension fact document. */
public final class EventFields {
    private final Map<String, Object> values;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public EventFields(Map<String, Object> values) {
        this.values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @JsonValue public Map<String, Object> asMap() { return values; }
    public static EventFields from(Map<String, Object> values) { return new EventFields(values); }

    public EventFields sanitized(List<FieldEvidence> evidence) {
        Set<String> supported = evidence == null ? Set.of() : evidence.stream().filter(Objects::nonNull)
                .filter(item -> item.field() != null && item.evidence() != null && !item.evidence().isBlank())
                .map(FieldEvidence::field).collect(java.util.stream.Collectors.toSet());
        Map<String, Object> clean = new LinkedHashMap<>();
        values.forEach((field, value) -> {
            Object sanitized = sanitize(field, value);
            if (sanitized != null) clean.put(field, sanitized);
        });
        return new EventFields(clean);
    }

    private Object sanitize(String field, Object value) {
        if (value == null) return null;
        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.isBlank() || normalized.equals("/") || Set.of("null", "none", "n/a", "na", "unknown", "not provided")
                    .contains(normalized.toLowerCase(Locale.ROOT))) return null;
            if (field.toLowerCase(Locale.ROOT).contains("date")) {
                try { return plausibleDateOrNull(LocalDate.parse(normalized)); } catch (Exception ignored) { return null; }
            }
            return normalized;
        }
        return value;
    }
    private static LocalDate plausibleDateOrNull(LocalDate value) {
        return value == null || value.getYear() < 2000 || value.isAfter(LocalDate.now().plusDays(1)) ? null : value;
    }
}
