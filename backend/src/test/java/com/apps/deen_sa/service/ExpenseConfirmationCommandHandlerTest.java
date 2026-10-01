package com.apps.deen_sa.service;

import com.apps.deen_sa.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.domain.TransactionDraftStatus;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ExpenseConfirmationCommandHandlerTest {
    private final TransactionDraftExtractionRepository repository =
            mock(TransactionDraftExtractionRepository.class);
    private final ConfirmedReferenceWriter referenceWriter =
            mock(ConfirmedReferenceWriter.class);
    private final FinancialTransactionWriter transactionWriter =
            mock(FinancialTransactionWriter.class);
    private final MissingTransactionDateContextService dateContexts =
            mock(MissingTransactionDateContextService.class);
    private final ExpenseConfirmationCommandHandler handler =
            new ExpenseConfirmationCommandHandler(
                    repository, referenceWriter, transactionWriter, dateContexts);

    @Test
    void confirmMarksExtractionUsedAndDraftConsumed() {
        TransactionDraftExtractionEntity extraction = activeExtraction();
        when(repository.findOwnedWhatsAppExtraction(5001L, "9198"))
                .thenReturn(Optional.of(extraction));

        handler.handle(new ExpenseConfirmationCommand(
                "9198", 5001L, ExpenseConfirmationCommand.Action.CONFIRM));

        assertThat(extraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.USED);
        assertThat(extraction.getDraft().getStatus()).isEqualTo(TransactionDraftStatus.CONSUMED);
        verify(referenceWriter).save(
                extraction, UserReferenceEntityType.MERCHANT, extraction.getMerchantName());
        verify(referenceWriter).save(
                extraction, UserReferenceEntityType.ACCOUNT, extraction.getSourceAccountName());
        verify(transactionWriter).save(extraction, null, null);
        verify(dateContexts).consumeForConfirmedDraft(extraction.getDraft());
    }

    @Test
    void discardRejectsExtractionAndCancelsDraft() {
        TransactionDraftExtractionEntity extraction = activeExtraction();
        when(repository.findOwnedWhatsAppExtraction(5001L, "9198"))
                .thenReturn(Optional.of(extraction));

        handler.handle(new ExpenseConfirmationCommand(
                "9198", 5001L, ExpenseConfirmationCommand.Action.DISCARD));

        assertThat(extraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.REJECTED);
        assertThat(extraction.getDraft().getStatus()).isEqualTo(TransactionDraftStatus.CANCELLED);
    }

    @Test void webConfirmationIsProfileScopedAndIdempotent() {
        var extraction=activeExtraction();extraction.setOccurredAt(java.time.LocalDate.parse("2026-09-28"));
        when(repository.findOwnedWebExtraction(5001L,42L)).thenReturn(Optional.of(extraction));
        handler.handleWeb(42L,5001L,true);handler.handleWeb(42L,5001L,true);
        verify(transactionWriter,org.mockito.Mockito.times(1)).save(extraction,null,null);
        org.mockito.Mockito.verifyNoInteractions(dateContexts);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->handler.handleWeb(99L,5001L,true))
                .isInstanceOf(com.apps.deen_sa.exception.WebApiException.class);
    }
    @Test void cancelledWebPreviewCannotBeConfirmed() {
        var extraction=activeExtraction();
        when(repository.findOwnedWebExtraction(5001L,42L)).thenReturn(Optional.of(extraction));
        handler.handleWeb(42L,5001L,false);handler.handleWeb(42L,5001L,false);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->handler.handleWeb(42L,5001L,true))
                .isInstanceOf(com.apps.deen_sa.exception.WebApiException.class);
        org.mockito.Mockito.verifyNoInteractions(transactionWriter,referenceWriter,dateContexts);
    }

    private TransactionDraftExtractionEntity activeExtraction() {
        TransactionDraftEntity draft = new TransactionDraftEntity();
        draft.setStatus(TransactionDraftStatus.PENDING);
        TransactionDraftExtractionEntity extraction = new TransactionDraftExtractionEntity();
        extraction.setDraft(draft);
        extraction.setStatus(TransactionDraftExtractionStatus.ACTIVE);
        return extraction;
    }
}
