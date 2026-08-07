package com.apps.deen_sa.core.ledger;

import com.apps.deen_sa.extension.api.ExecutionPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class GenericLedgerService {
    private final CoreEventRepository events;
    private final CoreMovementRepository movements;
    private final CoreObservationRepository observations;

    public GenericLedgerService(CoreEventRepository events, CoreMovementRepository movements,
                                CoreObservationRepository observations) {
        this.events = events; this.movements = movements; this.observations = observations;
    }

    @Transactional
    public CoreEventEntity commit(ExecutionPlan plan) {
        validate(plan);
        return events.findByTenantIdAndIdempotencyKey(plan.tenantId(), plan.idempotencyKey())
                .orElseGet(() -> commitNew(plan));
    }

    private CoreEventEntity commitNew(ExecutionPlan plan) {
        CoreEventEntity event = new CoreEventEntity();
        event.setTenantId(plan.tenantId()); event.setExtensionId(plan.extensionId()); event.setEventType(plan.eventType());
        event.setSchemaVersion(plan.schemaVersion()); event.setOccurredAt(plan.occurredAt()); event.setRecordedAt(Instant.now());
        event.setActorId(plan.actorId()); event.setStatus("COMMITTED"); event.setFacts(plan.facts());
        event.setEvidence(plan.evidence()); event.setRuleVersion(plan.ruleVersion());
        event.setIdempotencyKey(plan.idempotencyKey()); event.setCausationId(plan.causationId());
        event = events.save(event);
        final Long eventId = event.getId();
        plan.movements().forEach(value -> {
            CoreMovementEntity movement = new CoreMovementEntity(); movement.setEventId(eventId);
            movement.setResourceId(value.resourceId()); movement.setContainerId(value.containerId());
            movement.setQuantity(value.quantity()); movement.setUnitId(value.unitId()); movements.save(movement);
        });
        plan.observations().forEach(value -> {
            CoreObservationEntity observation = new CoreObservationEntity(); observation.setEventId(eventId);
            observation.setSubjectType(value.subjectType()); observation.setSubjectId(value.subjectId());
            observation.setValue(value.value()); observation.setUnitId(value.unitId());
            observation.setObservedAt(value.observedAt()); observations.save(observation);
        });
        return event;
    }

    private void validate(ExecutionPlan plan) {
        if (plan == null || plan.tenantId() == null || blank(plan.extensionId()) || blank(plan.eventType())
                || blank(plan.schemaVersion()) || plan.occurredAt() == null || blank(plan.actorId())
                || blank(plan.ruleVersion()) || blank(plan.idempotencyKey())) {
            throw new IllegalArgumentException("Execution plan is incomplete");
        }
        Map<String, String> resourceUnits = new HashMap<>();
        Map<String, java.math.BigDecimal> balances = new HashMap<>();
        plan.movements().forEach(movement -> {
            String prior = resourceUnits.putIfAbsent(movement.resourceId(), movement.unitId());
            if (prior != null && !prior.equals(movement.unitId())) {
                throw new IllegalArgumentException("Incompatible units for resource " + movement.resourceId());
            }
            balances.merge(movement.resourceId() + "|" + movement.unitId(), movement.quantity(), java.math.BigDecimal::add);
        });
        balances.forEach((resource, balance) -> {
            if (balance.signum() != 0) throw new IllegalArgumentException("Unbalanced movement plan for " + resource);
        });
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
