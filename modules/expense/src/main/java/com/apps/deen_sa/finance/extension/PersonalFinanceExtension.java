package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.extension.api.*;
import com.apps.deen_sa.finance.query.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Component
public class PersonalFinanceExtension implements BusinessExtension {
    private final List<EventCapability> events;
    private final List<QueryCapability> queries;
    private final StateContainerService containers;

    public PersonalFinanceExtension(List<SpeechHandler> handlers, QueryHandler queryHandler,
                                    StateContainerService containers, FinanceLedgerProjector projector,
                                    TransactionTemplate transactions) {
        this.containers = containers;
        List<EventCapability> discovered = new ArrayList<>(handlers.stream()
                .filter(handler -> !"QUERY".equalsIgnoreCase(handler.intentType()))
                .map(handler -> (EventCapability) new FinanceEventCapability(handler.intentType(), handler, projector, transactions)).toList());
        handler(handlers, "INVESTMENT").ifPresent(value -> discovered.add(new FinanceEventCapability("ASSET_BUY", value, projector, transactions)));
        handler(handlers, "INVESTMENT_SELL").ifPresent(value -> discovered.add(new FinanceEventCapability("ASSET_SELL", value, projector, transactions)));
        this.events = List.copyOf(discovered);
        this.queries = List.of(new FinanceQueryCapability(queryHandler));
    }

    @Override public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor("personal-finance", "1.0.0", 1, "Personal Finance", true,
                Set.of("en-IN", "ta-IN", "ta-Latn"), List.of("spent 500 on groceries", "salary credited", "card bill"));
    }
    @Override public Collection<EventCapability> events() { return events; }
    @Override public Collection<QueryCapability> queries() { return queries; }
    @Override public Collection<InterpretationPromptContributor> promptContributors() {
        return List.of(new FinanceInterpretationPrompt());
    }
    @Override public Collection<ContextContributor> contextContributors() {
        return List.of(new ContextContributor() {
            @Override public String namespace() { return "accounts"; }
            @Override public Object contribute(Long tenantId, Long userId) {
                return containers.getActiveContainers(userId).stream().map(PersonalFinanceExtension::account).toList();
            }
        });
    }

    private static Map<String, Object> account(StateContainerEntity account) {
        return Map.of("id", account.getId(), "name", account.getName(), "type", account.getContainerType(),
                "balanceKnown", account.getCurrentValue() != null);
    }

    private static Optional<SpeechHandler> handler(List<SpeechHandler> handlers, String type) {
        return handlers.stream().filter(value -> type.equalsIgnoreCase(value.intentType())).findFirst();
    }
}
