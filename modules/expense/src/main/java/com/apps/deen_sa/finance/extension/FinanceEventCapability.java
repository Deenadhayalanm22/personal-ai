package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.conversation.interpretation.StructuredEventHandler;
import com.apps.deen_sa.extension.api.*;
import com.apps.deen_sa.finance.expense.ExpenseHandler;

import java.util.Set;
import org.springframework.transaction.support.TransactionTemplate;

final class FinanceEventCapability implements EventCapability {
    private static final Set<String> FINANCE_FIELDS = Set.of("amount", "category", "subcategory", "merchantName",
            "sourceAccount", "destinationAccount", "sourceBalance", "creditLimit", "creditCardDueDay",
            "transactionDate", "tags", "rawText", "confirmBudget", "correctionChoice");
    private final String eventType;
    private final SpeechHandler delegate;
    private final FinanceLedgerProjector projector;
    private final TransactionTemplate transactions;

    FinanceEventCapability(String eventType, SpeechHandler delegate, FinanceLedgerProjector projector,
                           TransactionTemplate transactions) {
        this.eventType = eventType;
        this.delegate = delegate;
        this.projector = projector;
        this.transactions = transactions;
    }

    @Override public String eventType() { return eventType; }
    @Override public String schemaVersion() { return "1.0.0"; }
    @Override public Set<String> fields() { return FINANCE_FIELDS; }
    @Override public java.util.Map<String, String> fieldTypes() {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        fields().forEach(field -> values.put(field, "string"));
        java.util.Set.of("amount", "sourceBalance", "creditLimit").forEach(field -> values.put(field, "number"));
        values.put("creditCardDueDay", "integer"); values.put("tags", "array");
        return java.util.Map.copyOf(values);
    }
    @Override public String extractionInstructions() { return "Extract only explicitly evidenced personal-finance facts."; }

    @Override
    public CapabilityResult handle(ExtensionEvent extensionEvent, String rawText, CapabilityContext capabilityContext,
                                   boolean continuation) {
        if (!(extensionEvent instanceof EventPatch event) || !(capabilityContext instanceof ConversationContext context))
            throw new IllegalArgumentException("Finance compatibility adapter requires the host conversation bridge");
        SpeechResult result = transactions.execute(status -> executeAndProject(event, rawText, context, continuation));
        return toCapabilityResult(result);
    }

    private SpeechResult executeAndProject(EventPatch event, String rawText, ConversationContext context, boolean continuation) {
        SpeechResult result;
        if (delegate instanceof ExpenseHandler expense) {
            result = continuation ? expense.handleInterpretedFollowup(event, rawText, context)
                    : expense.handleInterpreted(event, rawText, context);
        } else if (delegate instanceof StructuredEventHandler structured) {
            result = structured.handleInterpreted(event, rawText, context);
        } else {
            result = continuation ? delegate.handleFollowup(rawText, context) : delegate.handleSpeech(rawText, context);
        }
        if (result != null && result.getStatus() == SpeechStatus.SAVED && !"EXPENSE_CORRECTION".equals(eventType))
            projector.project(event, result, rawText, context);
        return result;
    }

    private CapabilityResult toCapabilityResult(SpeechResult result) {
        if (result == null) return CapabilityResult.unknown("The finance capability returned no result.");
        return new CapabilityResult(result.getStatus().name(), result.getMessage(), Boolean.TRUE.equals(result.getNeedFollowup()),
                result.getMissingFields(), result.getPartial(), result.getSavedEntity(),
                result.getActions() == null ? java.util.List.of() : result.getActions().stream()
                        .map(value -> new CapabilityAction(value.id(), value.title())).toList(), result.getMedia());
    }
}
