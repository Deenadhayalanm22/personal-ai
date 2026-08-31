package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.extension.api.*;
import com.apps.deen_sa.finance.query.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Component
public class PersonalFinanceExtension implements BusinessExtension {
    private final List<EventCapability> events;
    private final List<QueryCapability> queries;

    public PersonalFinanceExtension(List<SpeechHandler> handlers, QueryHandler queryHandler,
                                    TransactionTemplate transactions) {
        List<EventCapability> discovered = new ArrayList<>(handlers.stream()
                .filter(handler -> !"QUERY".equalsIgnoreCase(handler.intentType()))
                .map(handler -> (EventCapability) new FinanceEventCapability(handler.intentType(), handler, transactions)).toList());
        this.events = List.copyOf(discovered);
        this.queries = List.of(new FinanceQueryCapability(queryHandler));
    }

    @Override public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor("personal-finance", "1.0.0", 1, "Personal Finance", true,
                Set.of("en-IN", "ta-IN", "ta-Latn"), List.of("spent 500 on groceries", "paid rent using UPI"));
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
                • செலவுகளை பதிவு செய்யலாம்
                • இன்று அல்லது இந்த மாத செலவை வகை வாரியாக பார்க்கலாம்

                இயல்பாகச் சொல்லுங்கள். உதாரணம்: “UPI மூலம் மளிகைக்கு 500 செலவு செய்தேன்.”
                """;
        return """
                I can help record operational activity through conversation.

                I can help with your personal expenses:
                • Record expenses
                • Show a high-level spending total and link you to detailed portal insights
                • Use the portal to edit or delete records

                Describe what happened naturally. For example: “I spent ₹500 on groceries using UPI.”
                """;
    }
    @Override public Collection<ContextContributor> contextContributors() { return List.of(); }
}
