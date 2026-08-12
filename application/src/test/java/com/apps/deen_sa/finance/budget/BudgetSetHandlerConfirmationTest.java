package com.apps.deen_sa.finance.budget;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.finance.expense.ExpenseCategoryResolver;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetSetHandlerConfirmationTest {
    private final MonthlyBudgetRepository budgets = mock(MonthlyBudgetRepository.class);
    private final ExpenseCategoryResolver categories = mock(ExpenseCategoryResolver.class);
    private final StateChangeRepository expenses = mock(StateChangeRepository.class);
    private final BudgetSetHandler handler = new BudgetSetHandler(budgets, categories, expenses);
    private final ConversationContext context = new ConversationContext();

    @BeforeEach
    void setUp() {
        context.setUserId(7L);
        when(categories.resolveBudgetScope(any(), any())).thenReturn(Optional.of("Groceries"));
    }

    @Test
    void rejectsScopeAbsentFromUsersConfirmedExpenses() {
        when(expenses.findExpenseScopes("7")).thenReturn(List.of());

        var result = handler.handleInterpreted(budgetEvent(), "Setup my grocery budget ₹5,000", context);

        assertThat(result.getMessage()).contains("confirmed expense history").contains("Add at least one expense");
        verify(budgets, never()).save(any());
    }

    @Test
    void previewsExistingUserSubcategoryThenPersistsOnlyAfterConfirm() {
        when(expenses.findExpenseScopes("7"))
                .thenReturn(List.<Object[]>of(new Object[]{"Food & Dining", "Groceries"}));
        when(budgets.findByUserIdAndCategoryIgnoreCase(7L, "Groceries")).thenReturn(Optional.empty());
        when(budgets.save(any())).thenAnswer(call -> call.getArgument(0));

        var preview = handler.handleInterpreted(budgetEvent(), "Setup my grocery budget ₹5,000", context);

        assertThat(preview.getStatus()).isEqualTo(SpeechStatus.FOLLOWUP);
        assertThat(preview.getMessage()).contains("Category: Food & Dining")
                .contains("Subcategory: Groceries").contains("Budget scope: Groceries");
        assertThat(preview.getActions()).extracting("title").containsExactly("Confirm", "Discard");
        verify(budgets, never()).save(any());

        var saved = handler.handleInterpreted(emptyEvent(), "CONFIRM_BUDGET", context);
        assertThat(saved.getStatus()).isEqualTo(SpeechStatus.SAVED);
        verify(budgets).save(any(MonthlyBudgetEntity.class));
    }

    @Test
    void discardDoesNotPersistBudget() {
        when(expenses.findExpenseScopes("7"))
                .thenReturn(List.<Object[]>of(new Object[]{"Food & Dining", "Groceries"}));
        handler.handleInterpreted(budgetEvent(), "Setup my grocery budget ₹5,000", context);

        var discarded = handler.handleInterpreted(emptyEvent(), "DISCARD_BUDGET", context);

        assertThat(discarded.getMessage()).contains("No budget was saved");
        verify(budgets, never()).save(any());
    }

    private EventPatch budgetEvent() {
        return new EventPatch(null, "BUDGET_SET", Map.of("category", "grocery",
                "amount", new BigDecimal("5000")), List.of(), List.of(), List.of());
    }

    private EventPatch emptyEvent() {
        return new EventPatch(null, "BUDGET_SET", Map.of(), List.of(), List.of(), List.of());
    }
}
