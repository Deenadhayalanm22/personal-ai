package com.apps.deen_sa.normalization;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.dto.*;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.insights.WebExpenseCaptureService;
import com.apps.deen_sa.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExpenseNormalizationHandlerTest {
    final WebExpenseCaptureService capture = mock(WebExpenseCaptureService.class);
    final AppUserService users = mock(AppUserService.class);
    final AppUserEntity user = new AppUserEntity();
    final TransactionDraftExtractionWriter writer = mock(TransactionDraftExtractionWriter.class);
    final ExpenseConfirmationPort confirmation = mock(ExpenseConfirmationPort.class);
    final MissingTransactionDateContextService dates = mock(MissingTransactionDateContextService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-09-02T01:00:00Z"), ZoneOffset.UTC);
    final ExpenseNormalizationHandler handler = new ExpenseNormalizationHandler(capture, writer, confirmation, clock, users, dates);
    final LocalDate today = LocalDate.of(2026,9,2);
    ExpenseNormalizationHandlerTest() { user.setId(42L); when(users.resolve("WHATSAPP", "9198")).thenReturn(user); }
    InboundMessage text() { return new InboundMessage("9198", "wamid.1", InputType.TEXT, MessageSource.WHATSAPP, "Paid 250 for groceries"); }
    WebExpenseCaptureService.Prepared facts(boolean ready, LocalDate date) {
        return new WebExpenseCaptureService.Prepared(new WebExpenseCaptureService.Facts(new BigDecimal("250.00"), date,
                "Food & Dining", "Groceries", "Shop", "Cash", ready), ready);
    }
    @Test void whatsappCallsExistingPortalCaptureAndConfirmsPersistedFacts() {
        when(capture.prepare(user,today,List.of(text().rawContent()))).thenReturn(facts(true,today));
        when(dates.applyToDraft(42L,text().rawContent(),today)).thenReturn(today.minusDays(1));
        var stored = new StoredDraftExtraction(5001L,42L,"9198",new BigDecimal("250.00"),"Shop","Cash",
                "Food & Dining","Groceries",today.minusDays(1),BigDecimal.ONE);
        when(writer.saveActive(any())).thenReturn(stored);
        handler.handle(new DraftWriteResult(42L,true),text());
        var normalized=ArgumentCaptor.forClass(NormalizedExpense.class);
        verify(writer).saveActive(normalized.capture());
        assertThat(normalized.getValue().amount()).isEqualByComparingTo("250");
        assertThat(normalized.getValue().transactionDate()).isEqualTo(today.minusDays(1));
        verify(confirmation).requestConfirmation(stored);
    }
    @Test void duplicateWebhookDoesNotRunCaptureAgain() {
        handler.handle(new DraftWriteResult(42L,false),text());
        verifyNoInteractions(capture,users,writer,confirmation);
    }
    @Test void audioWaitsForWordReviewBeforeUsingSharedCapture() {
        handler.handle(new DraftWriteResult(42L,true),new InboundMessage("9198","audio",InputType.AUDIO,MessageSource.WHATSAPP,"media_id=1"));
        verifyNoInteractions(capture,users,writer,confirmation);
    }
    @Test void incompleteOrIneligibleResultCancelsWithoutConfirmationOrDateHandoff() {
        when(capture.prepare(user,today,List.of(text().rawContent()))).thenReturn(facts(false,today));
        handler.handle(new DraftWriteResult(42L,true),text());
        verify(writer).cancelWithoutExtraction(42L);
        verify(confirmation).sendCaptureInstruction("9198", WebExpenseCaptureService.PORTAL_UPDATE);
        verify(writer,never()).saveActive(any());
        verifyNoInteractions(dates);
        verify(confirmation,never()).requestConfirmation(any());
    }
    @Test void whatsappUsesTheSameOwnerLocalTodayAsPortal() {
        user.setTimezone("America/Los_Angeles");
        LocalDate localToday=today.minusDays(1);
        when(capture.prepare(user,localToday,List.of(text().rawContent()))).thenReturn(facts(false,localToday));
        handler.handle(new DraftWriteResult(42L,true),text());
        verify(capture).prepare(user,localToday,List.of(text().rawContent()));
    }
    @Test void incompleteOrdinaryExpenseDirectsToPortalForDetailsWithoutConfirmation() {
        var prepared=new WebExpenseCaptureService.Prepared(new WebExpenseCaptureService.Facts(null,today,
                "Food & Dining","Groceries",null,null,true),false);
        when(capture.prepare(user,today,List.of(text().rawContent()))).thenReturn(prepared);
        handler.handle(new DraftWriteResult(42L,true),text());
        verify(confirmation).sendCaptureInstruction("9198",WebExpenseCaptureService.INCOMPLETE);
        verify(writer).cancelWithoutExtraction(42L);
        verify(writer,never()).saveActive(any());
        verify(confirmation,never()).requestConfirmation(any());
    }
}
