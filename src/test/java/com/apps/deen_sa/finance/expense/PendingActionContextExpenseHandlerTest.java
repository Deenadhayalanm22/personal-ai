package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.conversation.context.PendingActionContextService;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
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
    void requestsAllMissingExpenseFactsInOneNaturalLanguageFollowup() {
        ExpenseClassifier classifier = mock(ExpenseClassifier.class);
        StateChangeRepository repository = mock(StateChangeRepository.class);
        ExpenseCompletenessEvaluator completeness = mock(ExpenseCompletenessEvaluator.class);
        ExpenseInputNormalizer normalizer = mock(ExpenseInputNormalizer.class);
        ExpenseDraftService drafts = mock(ExpenseDraftService.class);
        ExpenseDto partial = new ExpenseDto();
        partial.setAmount(new BigDecimal("850"));
        partial.setTransactionDate(LocalDate.of(2026, 8, 14));
        when(classifier.extractExpense(anyString())).thenReturn(partial);
        when(normalizer.normalize(eq(partial), anyString(), any())).thenReturn(partial);
        when(completeness.evaluate(partial)).thenReturn(CompletenessLevelEnum.MINIMAL);
        ExpenseHandler handler = new ExpenseHandler(classifier, repository, completeness, normalizer,
                new ObjectMapper(), mock(TaxonomyCandidateService.class), drafts,
                mock(PendingActionContextService.class), new TransactionEnrichmentService());
        ConversationContext conversation = context();

        SpeechResult result = handler.handleSpeech("850 yesterday", conversation);

        assertThat(result.getMissingFields()).containsExactly("category", "subcategory");
        assertThat(result.getMessage()).contains("in one message");
        assertThat(conversation.getWaitingForField()).isEqualTo(ExpenseHandler.EXPENSE_COMPLETION);
        verify(drafts).capture(partial, conversation, java.util.List.of("category", "subcategory"));
    }

    @Test
    void confirmationOffersOptionalDetailsWithoutCreatingAnotherExpense() {
        ExpenseClassifier classifier = mock(ExpenseClassifier.class);
        ExpenseCompletenessEvaluator completeness = mock(ExpenseCompletenessEvaluator.class);
        ExpenseInputNormalizer normalizer = mock(ExpenseInputNormalizer.class);
        ExpenseDto expense = expense();
        when(classifier.extractExpense(anyString())).thenReturn(expense);
        when(normalizer.normalize(eq(expense), anyString(), any())).thenReturn(expense);
        when(completeness.evaluate(expense)).thenReturn(CompletenessLevelEnum.OPERATIONAL);
        ExpenseHandler handler = new ExpenseHandler(classifier, mock(StateChangeRepository.class), completeness, normalizer,
                new ObjectMapper(), mock(TaxonomyCandidateService.class), mock(ExpenseDraftService.class),
                mock(PendingActionContextService.class), new TransactionEnrichmentService());
        ConversationContext conversation = context();

        SpeechResult preview = handler.handleSpeech("spent 500 on fuel", conversation);
        SpeechResult addDetails = handler.handleFollowup("ADD_EXPENSE_DETAILS", conversation);

        assertThat(preview.getActions()).extracting(ResponseAction::title)
                .containsExactly("Confirm", "Add details", "Try again");
        assertThat(addDetails.getMessage()).contains("in one message");
        assertThat(conversation.getPartialObject()).isSameAs(expense);
        assertThat(conversation.getWaitingForField()).isEqualTo(ExpenseHandler.EXPENSE_DETAILS);
    }

    @Test
    void explicitRetryCorrectionMayUpdateCoreFactsWhileEnrichmentCannot() {
        ExpenseClassifier classifier = mock(ExpenseClassifier.class);
        ExpenseCompletenessEvaluator completeness = new ExpenseCompletenessEvaluator();
        ExpenseInputNormalizer normalizer = mock(ExpenseInputNormalizer.class);
        when(normalizer.normalize(any(ExpenseDto.class), anyString(), any()))
                .thenAnswer(call -> call.getArgument(0));
        ExpenseHandler handler = new ExpenseHandler(classifier, mock(StateChangeRepository.class), completeness, normalizer,
                new ObjectMapper(), mock(TaxonomyCandidateService.class), mock(ExpenseDraftService.class),
                mock(PendingActionContextService.class), new TransactionEnrichmentService());
        ExpenseDto expense = expense();
        expense.setMerchantName("Trends"); expense.setCategory("Shopping"); expense.setSubcategory("Clothing");
        ConversationContext conversation = context();
        conversation.setActiveIntent("EXPENSE"); conversation.setWaitingForField("confirmExpense");
        conversation.setPartialObject(expense);

        handler.handleFollowup("TRY_AGAIN_EXPENSE", conversation);
        handler.handleInterpretedFollowup(new EventPatch(null, "EXPENSE",
                java.util.Map.of("amount", 2800, "details", java.util.Map.of("beneficiary", "Wife")),
                java.util.List.of(), java.util.List.of(), java.util.List.of()),
                "Actually it was 2800 and for my wife", conversation);

        assertThat(expense.getAmount()).isEqualByComparingTo("2800");
        assertThat(expense.getDetails()).containsEntry("beneficiary", "Wife");
        assertThat(expense.getMerchantName()).isEqualTo("Trends");
    }

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
                new ObjectMapper(), candidates, drafts, contexts, new TransactionEnrichmentService());
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
