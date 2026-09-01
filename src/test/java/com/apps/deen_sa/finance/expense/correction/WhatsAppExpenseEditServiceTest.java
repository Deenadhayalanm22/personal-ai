package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.conversation.context.*;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.finance.legacy.state.*;
import com.apps.deen_sa.finance.tag.*;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WhatsAppExpenseEditServiceTest {
    @Test
    void understandsNaturalDoctorFeesPhraseWithoutModelClassification() {
        PendingActionContextRepository contexts = mock(PendingActionContextRepository.class);
        PendingActionContextEntity context = new PendingActionContextEntity();
        context.setContextType(PendingActionContextService.EDIT_TRANSACTION);
        context.setContextValue("95"); context.setStatus(PendingActionContextStatus.ACTIVE);
        context.setExpiresAt(Instant.now().plusSeconds(600));
        when(contexts.findActiveForUpdate(42L)).thenReturn(List.of(context));

        ExpenseClassifier classifier = mock(ExpenseClassifier.class);
        ExpenseCorrectionService corrections = mock(ExpenseCorrectionService.class);
        StateChangeEntity replacement = new StateChangeEntity(); replacement.setId(101L);
        when(corrections.editClassification(42L, 95L, "Medical", "Doctor Consultation"))
                .thenReturn(replacement);
        TransactionTagRepository tags = mock(TransactionTagRepository.class);
        when(tags.findAllByTransactionId(95L)).thenReturn(List.of());
        StateChangeRepository expenses = mock(StateChangeRepository.class);
        when(expenses.findExpenseForUpdate(95L, "42")).thenReturn(Optional.of(original()));
        WhatsAppExpenseEditService service = new WhatsAppExpenseEditService(contexts,
                mock(PendingActionContextService.class), expenses, corrections,
                classifier, new ExpenseTaxonomyRegistry(), tags);
        ConversationContext conversation = new ConversationContext(); conversation.setUserId(42L);

        SpeechResult review = service.processIfPending("WHATSAPP",
                "it is for medical expense and doctor fees", conversation).orElseThrow();

        assertThat(review.getStatus()).isEqualTo(SpeechStatus.FOLLOWUP);
        assertThat(review.getMessage()).contains("*Old message*", "*New message*", "~Food & Dining~", "*Medical*",
                "~Eating Out~", "*Doctor Consultation*", "Confirm this change?");
        verifyNoInteractions(corrections);

        SpeechResult confirmed = service.processIfPending("WHATSAPP", "CONFIRM_EXPENSE_EDIT", conversation)
                .orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(SpeechStatus.SAVED);
        assertThat(context.getStatus()).isEqualTo(PendingActionContextStatus.CONSUMED);
        verifyNoInteractions(classifier);
        verify(corrections).editClassification(42L, 95L, "Medical", "Doctor Consultation");
    }

    @Test
    void appliesClassificationToTargetExpenseAndConsumesContext() {
        PendingActionContextRepository contexts = mock(PendingActionContextRepository.class);
        PendingActionContextEntity context = new PendingActionContextEntity();
        context.setId("ctx_edit"); context.setUserId(42L);
        context.setContextType(PendingActionContextService.EDIT_TRANSACTION);
        context.setContextValue("95"); context.setStatus(PendingActionContextStatus.ACTIVE);
        context.setExpiresAt(Instant.now().plusSeconds(600));
        when(contexts.findActiveForUpdate(42L)).thenReturn(List.of(context));

        ExpenseClassifier classifier = mock(ExpenseClassifier.class);
        ExpenseDto extracted = new ExpenseDto();
        extracted.setCategory("Food & Dining"); extracted.setSubcategory("Eating Out");
        when(classifier.extractExpense("change it to eating out")).thenReturn(extracted);

        ExpenseTaxonomyRegistry taxonomy = mock(ExpenseTaxonomyRegistry.class);
        when(taxonomy.canonicalLabel("Food & Dining")).thenReturn(Optional.of("Food & Dining"));
        when(taxonomy.canonicalLabel("Eating Out")).thenReturn(Optional.of("Eating Out"));
        when(taxonomy.subcategoriesFor("Food & Dining")).thenReturn(Set.of("Eating Out"));

        ExpenseCorrectionService corrections = mock(ExpenseCorrectionService.class);
        StateChangeEntity replacement = new StateChangeEntity(); replacement.setId(101L);
        when(corrections.editClassification(42L, 95L, "Food & Dining", "Eating Out"))
                .thenReturn(replacement);
        TransactionTagRepository tags = mock(TransactionTagRepository.class);
        when(tags.findAllByTransactionId(95L)).thenReturn(List.of());
        StateChangeRepository expenses = mock(StateChangeRepository.class);
        when(expenses.findExpenseForUpdate(95L, "42")).thenReturn(Optional.of(original()));

        WhatsAppExpenseEditService service = new WhatsAppExpenseEditService(contexts,
                mock(PendingActionContextService.class), expenses, corrections,
                classifier, taxonomy, tags);
        ConversationContext conversation = new ConversationContext(); conversation.setUserId(42L);

        SpeechResult review = service.processIfPending("WHATSAPP", "change it to eating out", conversation)
                .orElseThrow();

        assertThat(review.getStatus()).isEqualTo(SpeechStatus.FOLLOWUP);
        assertThat(context.getStatus()).isEqualTo(PendingActionContextStatus.ACTIVE);
        verifyNoInteractions(corrections);

        SpeechResult result = service.processIfPending("WHATSAPP", "confirm", conversation).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(SpeechStatus.SAVED);
        assertThat(context.getStatus()).isEqualTo(PendingActionContextStatus.CONSUMED);
        verify(corrections).editClassification(42L, 95L, "Food & Dining", "Eating Out");
    }

    @Test
    void tryAgainKeepsContextActiveAndRestoresOriginalTarget() {
        PendingActionContextRepository contexts = mock(PendingActionContextRepository.class);
        PendingActionContextEntity context = new PendingActionContextEntity();
        context.setContextType(PendingActionContextService.CONFIRM_EDIT_TRANSACTION);
        context.setContextValue("95\nMedical\nDoctor Consultation");
        context.setStatus(PendingActionContextStatus.ACTIVE);
        context.setExpiresAt(Instant.now().plusSeconds(600));
        when(contexts.findActiveForUpdate(42L)).thenReturn(List.of(context));
        ExpenseCorrectionService corrections = mock(ExpenseCorrectionService.class);
        WhatsAppExpenseEditService service = new WhatsAppExpenseEditService(contexts,
                mock(PendingActionContextService.class), mock(StateChangeRepository.class), corrections,
                mock(ExpenseClassifier.class), mock(ExpenseTaxonomyRegistry.class),
                mock(TransactionTagRepository.class));
        ConversationContext conversation = new ConversationContext(); conversation.setUserId(42L);

        SpeechResult result = service.processIfPending("WHATSAPP", "TRY_AGAIN_EXPENSE_EDIT", conversation)
                .orElseThrow();

        assertThat(result.getMessage()).contains("Describe the category and subcategory again");
        assertThat(context.getStatus()).isEqualTo(PendingActionContextStatus.ACTIVE);
        assertThat(context.getContextType()).isEqualTo(PendingActionContextService.EDIT_TRANSACTION);
        assertThat(context.getContextValue()).isEqualTo("95");
        verifyNoInteractions(corrections);
    }

    private StateChangeEntity original() {
        StateChangeEntity value = new StateChangeEntity();
        value.setId(95L); value.setUserId("42"); value.setAmount(new java.math.BigDecimal("250"));
        value.setTimestamp(Instant.parse("2026-08-27T08:30:00Z"));
        value.setCategory("Food & Dining"); value.setSubcategory("Eating Out");
        value.setMainEntity("Clinic"); value.setRawText("paid 250 at clinic");
        value.setRecordStatus(com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE);
        return value;
    }
}
