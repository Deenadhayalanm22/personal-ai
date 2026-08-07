package com.apps.deen_sa.saree;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.conversation.interpretation.FieldEvidence;
import com.apps.deen_sa.core.ledger.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SareeEventCapabilityTest {
    @Test
    void materialIssueCreatesBalancedCustodyMovements() {
        List<CoreMovementEntity> savedMovements = new ArrayList<>();
        GenericLedgerService ledger = ledger(savedMovements);
        SareeEventCapability capability = new SareeEventCapability("SAREE_MATERIAL_ISSUED", ledger);
        ConversationContext context = new ConversationContext(); context.setUserId(7L); context.setSessionId(11L);
        String text = "issue 1000 metres thread to Lakshmi";
        EventPatch patch = new EventPatch(null, capability.eventType(), Map.of("rawText", text), List.of(), List.of(),
                List.of(new FieldEvidence("rawText", text, text, 1.0)));

        var response = capability.handle(patch, text, context, false);

        assertEquals("SAVED", response.status());
        assertEquals(2, savedMovements.size());
        assertEquals(new BigDecimal("-1000"), savedMovements.get(0).getQuantity());
        assertEquals(new BigDecimal("1000"), savedMovements.get(1).getQuantity());
        assertEquals("employee:lakshmi:custody", savedMovements.get(1).getContainerId());
    }

    @Test
    void acceptanceCreatesFinishedStockAndEffectiveWagePayable() {
        List<CoreMovementEntity> savedMovements = new ArrayList<>();
        SareeEventCapability capability = new SareeEventCapability("SAREE_PRODUCTION_ACCEPTED", ledger(savedMovements));
        ConversationContext context = new ConversationContext(); context.setUserId(7L); context.setSessionId(12L);
        String text = "accept 18 sarees from Lakshmi";
        EventPatch patch = new EventPatch(null, capability.eventType(), Map.of("rawText", text), List.of(), List.of(),
                List.of(new FieldEvidence("rawText", text, text, 1.0)));

        capability.handle(patch, text, context, false);

        assertEquals(4, savedMovements.size());
        assertEquals(new BigDecimal("1800"), savedMovements.get(3).getQuantity());
        assertEquals("INR", savedMovements.get(3).getUnitId());
    }

    private GenericLedgerService ledger(List<CoreMovementEntity> movementSink) {
        CoreEventRepository eventRepository = proxy(CoreEventRepository.class, (method, args) -> {
            if (method.equals("findByTenantIdAndIdempotencyKey")) return Optional.empty();
            if (method.equals("save")) { ((CoreEventEntity) args[0]).setId(99L); return args[0]; }
            return null;
        });
        CoreMovementRepository movementRepository = proxy(CoreMovementRepository.class, (method, args) -> {
            if (method.equals("save")) { movementSink.add((CoreMovementEntity) args[0]); return args[0]; }
            return null;
        });
        return new GenericLedgerService(eventRepository, movementRepository,
                proxy(CoreObservationRepository.class, (method, args) -> args == null ? null : args[0]));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (target, method, args) -> invocation.invoke(method.getName(), args));
    }
    private interface Invocation { Object invoke(String method, Object[] args); }
}
