package com.apps.deen_sa.finance.expense.draft;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpenseDraftServiceTest {
    private final ExpenseDraftRepository drafts = mock(ExpenseDraftRepository.class);
    private final ConversationSessionRepository sessions = mock(ConversationSessionRepository.class);
    private final ExpenseDraftService service = new ExpenseDraftService(drafts,
            new ObjectMapper().findAndRegisterModules(), new ExpenseTaxonomyRegistry(), sessions);

    @Test
    void persistsFirstWhatsAppCaptureAndLinksItToConversation() {
        when(drafts.save(any())).thenAnswer(call -> {
            ExpenseDraftEntity value = call.getArgument(0); value.setId(11L); return value;
        });
        ConversationContext context = new ConversationContext();
        context.setUserId(42L); context.setChannel("WHATSAPP");
        context.setMetadata(Map.of("inboundMessageId", "wamid-1"));
        ExpenseDto dto = expense(); dto.setCategory(null); dto.setSubcategory(null);

        ExpenseDraftEntity saved = service.capture(dto, context, List.of("category", "subcategory"));

        assertThat(saved.getStatus()).isEqualTo(ExpenseDraftStatus.PENDING);
        assertThat(saved.getSourceMessageId()).isEqualTo("wamid-1");
        assertThat(saved.getMissingFields()).containsExactly("category", "subcategory");
        assertThat(context.getActiveDraftId()).isEqualTo(11L);
    }

    @Test
    void portalUpdateIsVersionedAndDetachesStaleWhatsappFollowup() {
        ExpenseDraftEntity draft = draft();
        when(drafts.findOwnedForUpdate(11L, 42L)).thenReturn(Optional.of(draft));
        when(drafts.save(any())).thenAnswer(call -> call.getArgument(0));

        ExpenseDraftEntity updated = service.update(42L, 11L, 1,
                Map.of("category", "Travel", "subcategory", "Accommodation"));

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getMissingFields()).doesNotContain("category", "subcategory");
        verify(sessions).clearPendingDraft(42L, 11L);
    }

    @Test
    void completingDraftLinksFinalTransactionAndRemovesItFromPendingState() {
        ExpenseDraftEntity draft = draft();
        when(drafts.findOwnedForUpdate(11L, 42L)).thenReturn(Optional.of(draft));

        service.complete(42L, 11L, 99L);

        assertThat(draft.getStatus()).isEqualTo(ExpenseDraftStatus.COMPLETED);
        assertThat(draft.getCompletedTransactionId()).isEqualTo(99L);
        verify(sessions).clearPendingDraft(42L, 11L);
    }

    private ExpenseDraftEntity draft() {
        ExpenseDraftEntity value = new ExpenseDraftEntity();
        value.setId(11L); value.setUserId(42L); value.setSourceChannel("WHATSAPP");
        value.setRawText(expense().getRawText()); value.setPartialJson(
                new ObjectMapper().findAndRegisterModules().convertValue(expense(), Map.class));
        value.setMissingFields(List.of()); value.setStatus(ExpenseDraftStatus.PENDING); value.setVersion(1);
        return value;
    }

    private ExpenseDto expense() {
        ExpenseDto value = new ExpenseDto(); value.setValid(true); value.setAmount(new BigDecimal("3654"));
        value.setCategory("Travel"); value.setSubcategory("Accommodation"); value.setSourceAccount("BANK_ACCOUNT");
        value.setTransactionDate(LocalDate.of(2026, 8, 25));
        value.setRawText("booked room in oyo for pondicherry room for 3654 paid using upi");
        return value;
    }
}
