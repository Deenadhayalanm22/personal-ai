package com.apps.deen_sa.core.ledger;

import com.apps.deen_sa.extension.api.ExecutionPlan;
import com.apps.deen_sa.extension.api.MovementPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class GenericLedgerServiceTest {
    @Test
    void returnsExistingEventForDuplicateIdempotencyKey() {
        CoreEventEntity existing = new CoreEventEntity(); existing.setId(42L);
        CoreEventRepository events = proxy(CoreEventRepository.class, existing);
        GenericLedgerService service = new GenericLedgerService(events,
                proxy(CoreMovementRepository.class, null), proxy(CoreObservationRepository.class, null));
        assertSame(existing, service.commit(plan(List.of())));
    }

    @Test
    void rejectsDifferentUnitsForTheSameResourceInOnePlan() {
        List<MovementPlan> invalid = List.of(
                new MovementPlan("thread", "stock", new BigDecimal("-10"), "m"),
                new MovementPlan("thread", "worker", new BigDecimal("10"), "kg"));
        GenericLedgerService service = new GenericLedgerService(proxy(CoreEventRepository.class, null),
                proxy(CoreMovementRepository.class, null), proxy(CoreObservationRepository.class, null));
        assertThrows(IllegalArgumentException.class, () -> service.commit(plan(invalid)));
    }

    @Test
    void rejectsAnUnbalancedResourceMovementPlan() {
        GenericLedgerService service = new GenericLedgerService(proxy(CoreEventRepository.class, null),
                proxy(CoreMovementRepository.class, null), proxy(CoreObservationRepository.class, null));
        assertThrows(IllegalArgumentException.class, () -> service.commit(plan(List.of(
                new MovementPlan("thread", "worker", new BigDecimal("10"), "m")))));
    }

    private ExecutionPlan plan(List<MovementPlan> movements) {
        return new ExecutionPlan(1L, "test-extension", "TEST_EVENT", "1", Instant.now(), "user:1",
                Map.of(), Map.of(), "rules-1", "message-1", null, movements, List.of());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, CoreEventEntity existing) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getName().equals("findByTenantIdAndIdempotencyKey")) return Optional.ofNullable(existing);
            if (method.getName().equals("save")) return args[0];
            if (method.getReturnType().equals(boolean.class)) return false;
            if (method.getReturnType().equals(long.class)) return 0L;
            return null;
        });
    }
}
