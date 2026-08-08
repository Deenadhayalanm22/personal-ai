package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.conversation.interpretation.FieldEvidence;
import com.apps.deen_sa.core.ledger.*;
import com.apps.deen_sa.finance.persistence.FinanceAnalyticsRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinanceLedgerProjectorTest {
    @Test
    void expenseProjectsAsBalancedAccountAndExpenseMovements() {
        List<CoreMovementEntity> saved = new ArrayList<>();
        List<Object> projections = new ArrayList<>();
        FinanceAnalyticsRepository repository = proxy(FinanceAnalyticsRepository.class, (method, args) -> {
            if (method.equals("existsByCoreEventId")) return false;
            if (method.equals("save")) { projections.add(args[0]); return args[0]; }
            return null;
        });
        FinanceLedgerProjector projector = new FinanceLedgerProjector(ledger(saved), repository);
        ConversationContext context = new ConversationContext(); context.setUserId(3L); context.setSessionId(8L);
        EventPatch expense = new EventPatch(null, "EXPENSE",
                Map.of("amount", new BigDecimal("500"), "category", "Groceries", "sourceAccount", "Cash"),
                List.of(), List.of(), List.of(
                        new FieldEvidence("amount", "500", "500", 1.0),
                        new FieldEvidence("sourceAccount", "Cash", "Cash", 1.0)));

        projector.project(expense, "spent 500 on groceries using Cash", context);

        assertEquals(2, saved.size());
        assertEquals(new BigDecimal("-500"), saved.get(0).getQuantity());
        assertEquals("account:cash", saved.get(0).getContainerId());
        assertEquals(new BigDecimal("500"), saved.get(1).getQuantity());
        assertEquals("expense:groceries", saved.get(1).getContainerId());
        assertEquals(1, projections.size());
    }

    @Test
    void distinctExpensesFromOneMessageAndRepeatedButtonTextAreNotCollapsed() {
        Map<String, CoreEventEntity> events = new HashMap<>();
        Set<Long> projectedEventIds = new HashSet<>();
        long[] nextId = {1L};
        CoreEventRepository eventRepository = proxy(CoreEventRepository.class, (method, args) -> {
            if (method.equals("findByTenantIdAndIdempotencyKey"))
                return Optional.ofNullable(events.get(args[0] + "|" + args[1]));
            if (method.equals("save")) {
                CoreEventEntity event = (CoreEventEntity) args[0];
                event.setId(nextId[0]++);
                events.put(event.getTenantId() + "|" + event.getIdempotencyKey(), event);
                return event;
            }
            return null;
        });
        FinanceAnalyticsRepository projections = proxy(FinanceAnalyticsRepository.class, (method, args) -> {
            if (method.equals("existsByCoreEventId")) return projectedEventIds.contains(args[0]);
            if (method.equals("save")) {
                projectedEventIds.add(((com.apps.deen_sa.finance.persistence.FinanceExpenseProjectionEntity) args[0]).getCoreEventId());
                return args[0];
            }
            return null;
        });
        GenericLedgerService ledger = new GenericLedgerService(eventRepository,
                proxy(CoreMovementRepository.class, (method, args) -> args == null ? null : args[0]),
                proxy(CoreObservationRepository.class, (method, args) -> args == null ? null : args[0]));
        FinanceLedgerProjector projector = new FinanceLedgerProjector(ledger, projections);
        ConversationContext context = new ConversationContext();
        context.setUserId(3L); context.setSessionId(8L);
        context.setMetadata(new HashMap<>(Map.of("inboundMessageId", "wamid.multi")));

        EventPatch tea = expense("80", "Food");
        EventPatch auto = expense("120", "Transport");
        projector.project(tea, "Spent 80 on tea and 120 on auto using UPI", context);
        projector.project(auto, "Spent 80 on tea and 120 on auto using UPI", context);
        projector.project(tea, "Spent 80 on tea and 120 on auto using UPI", context);

        assertEquals(2, events.size(), "two expenses in one inbound message need separate ledger events");
        assertEquals(2, projectedEventIds.size(), "a retry of the same expense must remain idempotent");

        context.setMetadata(Map.of());
        projector.project(expense("650", "Utilities"), "Bank / UPI", context);
        projector.project(expense("450", "Food"), "Bank / UPI", context);

        assertEquals(4, events.size(), "different follow-up completions may share the same button text");
        assertEquals(4, projectedEventIds.size());
    }

    private EventPatch expense(String amount, String category) {
        return new EventPatch(null, "EXPENSE",
                Map.of("amount", new BigDecimal(amount), "category", category, "sourceAccount", "My bank account"),
                List.of(), List.of(), List.of());
    }

    private GenericLedgerService ledger(List<CoreMovementEntity> sink) {
        return new GenericLedgerService(proxy(CoreEventRepository.class, (method, args) -> {
            if (method.equals("findByTenantIdAndIdempotencyKey")) return Optional.empty();
            if (method.equals("save")) { ((CoreEventEntity) args[0]).setId(1L); return args[0]; }
            return null;
        }), proxy(CoreMovementRepository.class, (method, args) -> {
            if (method.equals("save")) { sink.add((CoreMovementEntity) args[0]); return args[0]; }
            return null;
        }), proxy(CoreObservationRepository.class, (method, args) -> args == null ? null : args[0]));
    }
    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (target, method, args) -> invocation.invoke(method.getName(), args));
    }
    private interface Invocation { Object invoke(String method, Object[] args); }
}
