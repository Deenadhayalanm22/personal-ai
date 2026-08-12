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
    @Override public Collection<DeterministicEventRouter> deterministicRouters() {
        return List.of(new FinanceDeterministicEventRouter());
    }
    @Override public Collection<InterpretationPromptContributor> promptContributors() {
        return List.of(new FinanceInterpretationPrompt());
    }
    @Override public String help(String locale) {
        if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("ta")) return """
                உங்கள் தனிப்பட்ட செலவுகளை நிர்வகிக்க நான் உதவ முடியும்:
                • செலவு மற்றும் வருமானத்தை பதிவு செய்யலாம்
                • வங்கி, UPI மற்றும் கிரெடிட் கார்டு கணக்குகளை அமைக்கலாம்
                • கார்டு பில் மற்றும் கணக்கு இடமாற்றங்களை பதிவு செய்யலாம்
                • இன்று அல்லது இந்த மாத செலவை வகை வாரியாக பார்க்கலாம்
                • மாதாந்திர பட்ஜெட்டை அமைத்து மீதியை பார்க்கலாம்
                • வரவிருக்கும் கிரெடிட் கார்டு கட்டணங்களை பார்க்கலாம்

                இயல்பாகச் சொல்லுங்கள். உதாரணம்: “UPI மூலம் மளிகைக்கு 500 செலவு செய்தேன்.”
                """;
        return """
                I can help record operational activity through conversation.

                I can help with your personal expenses:
                • Record expenses and income
                • Set up bank, UPI, cash, and credit-card accounts
                • Record card-bill payments and transfers
                • Show spending totals and category breakdowns for today or this month
                • Set monthly category budgets and check what remains
                • Show upcoming credit-card payment reminders
                • Find, edit, or delete an earlier expense safely

                Describe what happened naturally. For example: “I spent ₹500 on groceries using UPI.”
                """;
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
