package com.apps.deen_sa.v2.normalization;

import com.apps.deen_sa.v2.dto.DraftWriteResult;
import com.apps.deen_sa.v2.dto.InboundMessage;
import com.apps.deen_sa.v2.domain.InputType;
import com.apps.deen_sa.v2.domain.MessageSource;
import com.apps.deen_sa.v2.dto.NormalizedExpense;
import com.apps.deen_sa.v2.dto.StoredDraftExtraction;
import com.apps.deen_sa.v2.service.TransactionDraftExtractionWriter;
import com.apps.deen_sa.v2.service.V2MissingTransactionDateContextService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseNormalizationHandlerTest {
    private final ExpenseNormalizationPort normalizer = mock(ExpenseNormalizationPort.class);
    private final TransactionDraftExtractionWriter extractionWriter =
            mock(TransactionDraftExtractionWriter.class);
    private final ExpenseConfirmationPort confirmation = mock(ExpenseConfirmationPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T10:00:00Z"), ZoneOffset.UTC);
    private final V2MissingTransactionDateContextService dateContexts =
            mock(V2MissingTransactionDateContextService.class);
    private final ExpenseNormalizationHandler handler =
            new ExpenseNormalizationHandler(
                    normalizer, extractionWriter, confirmation, clock, dateContexts);

    @Test
    void normalizesCommittedTextAndRequestsConfirmationWithoutPersistingIt() {
        InboundMessage message = new InboundMessage(
                "9198", "wamid.1", InputType.TEXT, MessageSource.WHATSAPP,
                "Paid ₹250 at Swiggy");
        when(normalizer.normalize("9198", "Paid ₹250 at Swiggy", LocalDate.of(2026, 9, 2)))
                .thenReturn(new ExpenseNormalizationPort.ExpenseFacts(
                        new BigDecimal("250"),
                        "Food & Dining",
                        "Eating Out",
                        "Swiggy",
                        LocalDate.of(2026, 9, 2),
                        new BigDecimal("0.94")));
        when(dateContexts.applyToDraft(
                42L, "Paid ₹250 at Swiggy", LocalDate.of(2026, 9, 2)))
                .thenReturn(LocalDate.of(2026, 9, 2));
        StoredDraftExtraction stored = new StoredDraftExtraction(
                5001L, 42L, "9198", new BigDecimal("250"), "Swiggy",
                "Food & Dining", "Eating Out", LocalDate.of(2026, 9, 2),
                new BigDecimal("0.94"));
        when(extractionWriter.saveActive(org.mockito.ArgumentMatchers.any()))
                .thenReturn(stored);

        handler.handle(new DraftWriteResult(42L, true), message);

        ArgumentCaptor<NormalizedExpense> normalized =
                ArgumentCaptor.forClass(NormalizedExpense.class);
        verify(extractionWriter).saveActive(normalized.capture());
        assertThat(normalized.getValue()).isEqualTo(new NormalizedExpense(
                42L,
                "9198",
                new BigDecimal("250"),
                "Food & Dining",
                "Eating Out",
                "Swiggy",
                LocalDate.of(2026, 9, 2),
                new BigDecimal("0.94")));
        verify(confirmation).requestConfirmation(stored);
    }

    @Test
    void doesNotRepeatAiWorkForDuplicateWebhook() {
        handler.handle(new DraftWriteResult(42L, false), textMessage());

        verify(normalizer, never()).normalize("9198", "Paid ₹250", LocalDate.of(2026, 9, 2));
        verify(confirmation, never()).requestConfirmation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void waitsForAudioTranscriptionBeforeNormalization() {
        InboundMessage audio = new InboundMessage(
                "9198", "wamid.audio", InputType.AUDIO, MessageSource.WHATSAPP,
                "media_id=1;mime_type=audio/ogg");

        handler.handle(new DraftWriteResult(42L, true), audio);

        verify(normalizer, never()).normalize(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(confirmation, never()).requestConfirmation(org.mockito.ArgumentMatchers.any());
    }

    private InboundMessage textMessage() {
        return new InboundMessage(
                "9198", "wamid.1", InputType.TEXT, MessageSource.WHATSAPP, "Paid ₹250");
    }
}
