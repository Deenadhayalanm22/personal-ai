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

        WhatsAppExpenseEditService service = new WhatsAppExpenseEditService(contexts,
                mock(PendingActionContextService.class), mock(StateChangeRepository.class), corrections,
                classifier, taxonomy, tags);
        ConversationContext conversation = new ConversationContext(); conversation.setUserId(42L);

        SpeechResult result = service.processIfPending("WHATSAPP", "change it to eating out", conversation)
                .orElseThrow();

        assertThat(result.getStatus()).isEqualTo(SpeechStatus.SAVED);
        assertThat(context.getStatus()).isEqualTo(PendingActionContextStatus.CONSUMED);
        verify(corrections).editClassification(42L, 95L, "Food & Dining", "Eating Out");
    }
}
