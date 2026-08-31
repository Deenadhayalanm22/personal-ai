package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.conversation.context.PendingActionContextService;
import com.apps.deen_sa.finance.expense.draft.ExpenseDraftService;
import com.apps.deen_sa.finance.legacy.state.*;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PendingActionContextExpenseHandlerTest {
    @Test
    void consumesPendingActionContextOnlyWhenExpenseIsSuccessfullyConfirmed() {
        ExpenseClassifier classifier = mock(ExpenseClassifier.class);
        StateChangeRepository repository = mock(StateChangeRepository.class);
        ExpenseCompletenessEvaluator completeness = mock(ExpenseCompletenessEvaluator.class);
        ExpenseInputNormalizer normalizer = mock(ExpenseInputNormalizer.class);
        TaxonomyCandidateService candidates = mock(TaxonomyCandidateService.class);
        ExpenseDraftService drafts = mock(ExpenseDraftService.class);
        PendingActionContextService contexts = mock(PendingActionContextService.class);
        ExpenseDto expense = expense();
        when(classifier.extractExpense(anyString())).thenReturn(expense);
        when(completeness.evaluate(expense)).thenReturn(CompletenessLevelEnum.OPERATIONAL);
        when(contexts.consumeIfActive(42L, "ctx_123")).thenReturn(true);
        StateChangeEntity saved = new StateChangeEntity(); saved.setId(9L);
        when(repository.save(any())).thenReturn(saved);
        ExpenseHandler handler = new ExpenseHandler(classifier, repository, completeness, normalizer,
                new ObjectMapper(), candidates, drafts, contexts);
        ConversationContext conversation = context();
        when(normalizer.normalize(expense, "spent 500 on fuel", conversation)).thenReturn(expense);

        SpeechResult preview = handler.handleSpeech("spent 500 on fuel", conversation);
        verify(contexts, never()).consumeIfActive(anyLong(), any());
        SpeechResult confirmed = handler.handleFollowup("confirm", conversation);

        assertThat(preview.getStatus()).isEqualTo(SpeechStatus.FOLLOWUP);
        assertThat(confirmed.getStatus()).isEqualTo(SpeechStatus.SAVED);
        assertThat(confirmed.getMessage()).isEqualTo("Added ₹500 for Fuel on 14 August.");
        verify(contexts).consumeIfActive(42L, "ctx_123");
        verify(repository).save(any(StateChangeEntity.class));
    }

    private ExpenseDto expense() {
        ExpenseDto dto = new ExpenseDto();
        dto.setAmount(new BigDecimal("500")); dto.setCategory("Transportation"); dto.setSubcategory("Fuel");
        dto.setTransactionDate(LocalDate.of(2026, 8, 14)); dto.setPendingActionContextId("ctx_123");
        dto.setContextDateApplied(true); dto.setRawText("spent 500 on fuel");
        return dto;
    }

    private ConversationContext context() {
        ConversationContext context = new ConversationContext();
        context.setUserId(42L); context.setChannel("WHATSAPP"); context.setTimezone("Asia/Kolkata");
        return context;
    }
}
