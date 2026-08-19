package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.conversation.interpretation.FieldEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseCorrectionHandlerTest {
    private final ExpenseCorrectionFinder finder = mock(ExpenseCorrectionFinder.class);
    private final ExpenseCorrectionService service = mock(ExpenseCorrectionService.class);
    private final StateChangeRepository transactions = mock(StateChangeRepository.class);
    private final StateContainerRepository containers = mock(StateContainerRepository.class);
    private ExpenseCorrectionHandler handler;
    private ConversationContext context;
    private StateChangeEntity expense;

    @BeforeEach
    void setUp() {
        handler = new ExpenseCorrectionHandler(finder, service, transactions, containers);
        context = new ConversationContext();
        context.setUserId(7L);
        context.setTimezone("Asia/Kolkata");
        expense = new StateChangeEntity();
        expense.setId(42L); expense.setUserId("7"); expense.setTransactionType(StateChangeTypeEnum.EXPENSE);
        expense.setRecordStatus(ExpenseRecordStatus.ACTIVE); expense.setAmount(new BigDecimal("850"));
        expense.setMainEntity("Swiggy"); expense.setCategory("Food"); expense.setTimestamp(Instant.parse("2026-08-12T08:00:00Z"));
        when(finder.find(org.mockito.ArgumentMatchers.eq("7"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExpenseBrowsePage(List.of(expense), null));
        when(transactions.findById(42L)).thenReturn(Optional.of(expense));
    }

    @Test
    void editIntentBrowsesThenGoesDirectlyToFieldSelection() {
        SpeechResult browse = handler.handleInterpreted(event("EDIT", null, null), "I want to edit", context);

        assertThat(browse.getMessage()).contains("Is this the transaction you want to edit?", "₹850", "Swiggy");
        assertThat(browse.getActions()).extracting("title").containsExactly("Yes", "Cancel");

        SpeechResult fields = handler.handleFollowup("SELECT_42", context);
        assertThat(fields.getMessage()).contains("What do you want to change?");
        assertThat(fields.getActions()).extracting("title")
                .containsExactly("Amount", "Category", "Merchant", "Date", "Account", "Cancel");
    }

    @Test
    void aiCategoryScopeIsPassedToTheFinderWithoutMerchantParsing() {
        handler.handleInterpreted(event("EDIT", "Food & Dining", "Groceries"),
                "எனது மளிகை செலவைத் திருத்து", context);

        org.mockito.ArgumentCaptor<ExpenseCorrectionState> state =
                org.mockito.ArgumentCaptor.forClass(ExpenseCorrectionState.class);
        verify(finder).find(org.mockito.ArgumentMatchers.eq("7"), state.capture());
        assertThat(state.getValue().getCategory()).isEqualTo("Food & Dining");
        assertThat(state.getValue().getSubcategory()).isEqualTo("Groceries");
        assertThat(state.getValue().getAction()).isEqualTo(CorrectionAction.EDIT);
    }

    @Test
    void deleteRequiresExplicitConfirmationBeforeVoiding() {
        handler.handleInterpreted(event("DELETE", null, null), "delete a transaction", context);
        SpeechResult confirmation = handler.handleFollowup("SELECT_42", context);

        assertThat(confirmation.getMessage()).contains("Delete this transaction?", "voided");
        assertThat(confirmation.getActions()).extracting("title").containsExactly("Delete transaction", "Cancel");

        when(service.voidExpense(7L, 42L)).thenReturn(new CorrectionOutcome(expense, null, "₹850 was restored"));
        SpeechResult completed = handler.handleFollowup("CONFIRM", context);

        assertThat(completed.getMessage()).contains("Transaction deleted", "₹850 was restored");
        assertThat(context.isInFollowup()).isFalse();
        verify(service).voidExpense(7L, 42L);
    }

    @Test
    void multipleMatchesKeepTheNumberedSelectionFlow() {
        StateChangeEntity second = new StateChangeEntity();
        second.setId(43L); second.setAmount(new BigDecimal("300")); second.setMainEntity("Groceries");
        second.setTimestamp(Instant.parse("2026-08-13T08:00:00Z"));
        when(finder.find(org.mockito.ArgumentMatchers.eq("7"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExpenseBrowsePage(List.of(second, expense), null));

        SpeechResult browse = handler.handleInterpreted(event("EDIT", null, "Groceries"),
                "edit my grocery expenses", context);

        assertThat(browse.getMessage()).contains("Select a transaction", "1. ₹300", "2. ₹850");
        assertThat(browse.getActions()).extracting("title").containsExactly("1", "2", "Cancel");
    }

    private EventPatch event(String action, String category, String subcategory) {
        java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
        java.util.List<FieldEvidence> evidence = new java.util.ArrayList<>();
        if (action != null) { fields.put("correctionAction", action); evidence.add(new FieldEvidence(
                "correctionAction", action, action.toLowerCase(), 1.0)); }
        if (category != null) { fields.put("category", category); evidence.add(new FieldEvidence(
                "category", category, category, 1.0)); }
        if (subcategory != null) { fields.put("subcategory", subcategory); evidence.add(new FieldEvidence(
                "subcategory", subcategory, subcategory, 1.0)); }
        return new EventPatch(null, "EXPENSE_CORRECTION", fields, List.of(), List.of(), evidence);
    }
}
