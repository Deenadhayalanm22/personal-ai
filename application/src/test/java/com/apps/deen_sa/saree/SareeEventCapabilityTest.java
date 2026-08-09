package com.apps.deen_sa.saree;

import com.apps.deen_sa.conversation.ConversationContext;
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
    void assignmentCreatesSw101WithoutChangingOwnerInventory() {
        List<CoreMovementEntity> movements = new ArrayList<>();
        SareeEventCapability capability = new SareeEventCapability(SareeEventCapability.MATERIAL_ISSUED,
                ledger(movements), workflow(List.of()));

        var result = capability.handle(patch(capability.eventType(), "Give Selvi 1,000 metres of thread today."),
                "Give Selvi 1,000 metres of thread today.", context("wamid.issue"), false);

        assertEquals("SAVED", result.status());
        assertTrue(result.message().contains("SW-101"));
        assertEquals(2, movements.size());
        assertEquals("assignment-source", movements.get(0).getContainerId());
        assertEquals("batch:SW-101:assigned", movements.get(1).getContainerId());
        assertEquals(new BigDecimal("1000"), movements.get(1).getQuantity());
        assertTrue(movements.stream().noneMatch(value -> "raw-material-stock".equals(value.getContainerId())));
    }

    @Test
    void surrenderAutomaticallyAcceptsAndEarnsWage() {
        CoreEventEntity assignment = event(1L, SareeEventCapability.MATERIAL_ISSUED, "wamid.issue",
                Map.of("employee", "Selvi", "batchId", "SW-101", "batchStatus", "OPEN"));
        List<CoreMovementEntity> movements = new ArrayList<>();
        SareeEventCapability capability = new SareeEventCapability(SareeEventCapability.PRODUCTION_SURRENDERED,
                ledger(movements), workflow(List.of(assignment)));

        var result = capability.handle(patch(capability.eventType(), "Selvi surrendered 24 sarees."),
                "Selvi surrendered 24 sarees.", context("wamid.surrender"), false);

        assertEquals("SAVED", result.status());
        assertTrue(result.message().contains("₹2,400"));
        assertEquals(4, movements.size());
        assertEquals("batch:SW-101:accepted", movements.get(1).getContainerId());
        assertEquals(new BigDecimal("2400"), movements.get(3).getQuantity());
        assertEquals("employee:selvi:earned", movements.get(3).getContainerId());
    }

    @Test
    void liveModelEmployeeFieldWinsOverPronounLikeLotReference() {
        CoreEventEntity assignment = event(1L, SareeEventCapability.MATERIAL_ISSUED, "wamid.issue",
                Map.of("employee", "Selvi", "batchId", "SW-101", "batchStatus", "OPEN"));
        List<CoreMovementEntity> movements = new ArrayList<>();
        SareeEventCapability capability = new SareeEventCapability(SareeEventCapability.PRODUCTION_SURRENDERED,
                ledger(movements), workflow(List.of(assignment)));
        String text = "Selvi brought back 24 completed sarees from that lot.";
        EventPatch modelPatch = new EventPatch(null, capability.eventType(),
                Map.of("employee", "Selvi", "quantity", new BigDecimal("24"), "rawText", text),
                List.of(), List.of(), List.of(
                new FieldEvidence("employee", "Selvi", "Selvi", 1.0),
                new FieldEvidence("quantity", "24", "24", 1.0)));

        var result = capability.handle(modelPatch, text, context("wamid.surrender-live"), false);

        assertEquals("SAVED", result.status());
        assertTrue(result.message().startsWith("Recorded 24 sarees for SW-101. Selvi earned"));
        assertEquals("batch:SW-101:accepted", movements.get(1).getContainerId());
    }

    @Test
    void targetConversationRoutesWithoutCallingTheModel() {
        SareeJobWorkExtension extension = new SareeJobWorkExtension(ledger(new ArrayList<>()),
                proxy(SareeLedgerQueryRepository.class, (method, args) -> BigDecimal.ZERO), workflow(List.of()));

        assertEquals(Optional.of(SareeEventCapability.MATERIAL_ISSUED), route(extension, "Give Selvi 1,000 metres of thread today."));
        assertEquals(Optional.of(SareeEventCapability.PRODUCTION_SURRENDERED), route(extension, "Selvi surrendered 24 sarees."));
        assertEquals(Optional.of(SareeEventCapability.WAGE_STATEMENT_APPROVED), route(extension, "Yes."));
        assertEquals(Optional.of(SareeEventCapability.WAGE_PAID), route(extension, "Paid ₹2,400 cash today."));
        assertEquals(Optional.of(SareeEventCapability.WAGE_PAID), route(extension, "₹2,400 in cash, today."));
    }

    private Optional<String> route(SareeJobWorkExtension extension, String text) {
        return extension.deterministicRouters().iterator().next().eventType(text);
    }

    private EventPatch patch(String type, String text) {
        return new EventPatch(null, type, Map.of("rawText", text), List.of(), List.of(),
                List.of(new FieldEvidence("rawText", text, text, 1.0)));
    }

    private ConversationContext context(String messageId) {
        ConversationContext context = new ConversationContext();
        context.setUserId(7L);
        context.setSessionId(11L);
        context.setMetadata(Map.of("tenantId", 7L, "inboundMessageId", messageId));
        return context;
    }

    private CoreEventEntity event(Long id, String type, String key, Map<String, Object> facts) {
        CoreEventEntity event = new CoreEventEntity();
        event.setId(id);
        event.setEventType(type);
        event.setIdempotencyKey(key);
        event.setFacts(facts);
        return event;
    }

    private SareeWorkflowRepository workflow(List<CoreEventEntity> events) {
        return proxy(SareeWorkflowRepository.class, (method, args) -> {
            if (method.equals("countByTenantIdAndExtensionIdAndEventType")) return 0L;
            if (method.equals("findByTenantIdAndExtensionIdAndEventTypeOrderByIdDesc")) {
                String type = String.valueOf(args[2]);
                return events.stream().filter(value -> type.equals(value.getEventType())).toList();
            }
            return null;
        });
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
