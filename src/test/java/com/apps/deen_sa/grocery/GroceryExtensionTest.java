package com.apps.deen_sa.grocery;

import com.apps.deen_sa.core.ledger.CoreEventEntity;
import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.extension.api.CapabilityContext;
import com.apps.deen_sa.extension.api.CapabilityResult;
import com.apps.deen_sa.extension.api.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import com.apps.deen_sa.core.ledger.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class GroceryExtensionTest {
    @Test
    void installsAsAnIndependentExtensionAndBalancesStockMovements() {
        List<CoreMovementEntity> savedMovements = new ArrayList<>();
        GenericLedgerService ledger = ledger(savedMovements);
        GroceryExtension extension = new GroceryExtension(ledger);
        CapabilityContext context = context();

        CapabilityResult result = extension.events().stream()
                .filter(value -> value.eventType().equals("GROCERY_STOCK_RECEIVED"))
                .findFirst().orElseThrow().handle(null, "received 20 kg rice", context, false);

        assertThat(result.status()).isEqualTo("SAVED");
        assertThat(extension.descriptor().installForNewTenant()).isFalse();
        assertThat(savedMovements).hasSize(2);
        assertThat(savedMovements).extracting(value -> value.getQuantity().signum())
                .containsExactly(-1, 1);
    }

    private CapabilityContext context() {
        return (CapabilityContext) Proxy.newProxyInstance(CapabilityContext.class.getClassLoader(),
                new Class<?>[]{CapabilityContext.class}, (target, method, args) -> switch (method.getName()) {
                    case "getUserId" -> 7L;
                    case "getSessionId" -> 9L;
                    case "getMetadata" -> Map.of("tenantId", 7L, "inboundMessageId", "wamid-1");
                    case "isInFollowup" -> false;
                    default -> null;
                });
    }

    private GenericLedgerService ledger(List<CoreMovementEntity> sink) {
        CoreEventRepository events = proxy(CoreEventRepository.class, (method, args) -> {
            if (method.equals("findByTenantIdAndIdempotencyKey")) return java.util.Optional.empty();
            if (method.equals("save")) { ((CoreEventEntity) args[0]).setId(1L); return args[0]; }
            return null;
        });
        CoreMovementRepository movements = proxy(CoreMovementRepository.class, (method, args) -> {
            if (method.equals("save")) { sink.add((CoreMovementEntity) args[0]); return args[0]; }
            return null;
        });
        return new GenericLedgerService(events, movements,
                proxy(CoreObservationRepository.class, (method, args) -> args == null ? null : args[0]));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (target, method, args) -> invocation.invoke(method.getName(), args));
    }
    private interface Invocation { Object invoke(String method, Object[] args); }
}
