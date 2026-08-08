package com.apps.deen_sa.extension.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExecutionPlan(
        Long tenantId, String extensionId, String eventType, String schemaVersion,
        Instant occurredAt, String actorId, Map<String, Object> facts, Map<String, Object> evidence,
        String ruleVersion, String idempotencyKey, String causationId,
        List<MovementPlan> movements, List<ObservationPlan> observations
) {
    public ExecutionPlan {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        movements = movements == null ? List.of() : List.copyOf(movements);
        observations = observations == null ? List.of() : List.copyOf(observations);
    }
}
