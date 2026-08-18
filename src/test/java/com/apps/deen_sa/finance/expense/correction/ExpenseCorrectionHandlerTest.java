package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
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
        SpeechResult browse = handler.handleSpeech("I want to edit", context);

        assertThat(browse.getMessage()).contains("Select a transaction", "₹850", "Swiggy");
        assertThat(browse.getActions()).extracting("title").containsExactly("1", "Cancel");

        SpeechResult fields = handler.handleFollowup("1", context);
        assertThat(fields.getMessage()).contains("What do you want to change?");
        assertThat(fields.getActions()).extracting("title")
                .containsExactly("Amount", "Category", "Merchant", "Date", "Account", "Cancel");
    }

    @Test
    void deleteRequiresExplicitConfirmationBeforeVoiding() {
        handler.handleSpeech("delete a transaction", context);
        SpeechResult confirmation = handler.handleFollowup("SELECT_42", context);

        assertThat(confirmation.getMessage()).contains("Delete this transaction?", "voided");
        assertThat(confirmation.getActions()).extracting("title").containsExactly("Delete transaction", "Cancel");

        when(service.voidExpense(7L, 42L)).thenReturn(new CorrectionOutcome(expense, null, "₹850 was restored"));
        SpeechResult completed = handler.handleFollowup("CONFIRM", context);

        assertThat(completed.getMessage()).contains("Transaction deleted", "₹850 was restored");
        assertThat(context.isInFollowup()).isFalse();
        verify(service).voidExpense(7L, 42L);
    }
}
