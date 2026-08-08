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
