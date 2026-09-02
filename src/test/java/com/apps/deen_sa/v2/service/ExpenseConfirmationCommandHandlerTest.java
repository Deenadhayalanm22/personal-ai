package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.v2.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.v2.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.v2.domain.TransactionDraftStatus;
import com.apps.deen_sa.v2.entity.TransactionDraftEntity;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.v2.repository.TransactionDraftExtractionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ExpenseConfirmationCommandHandlerTest {
    private final TransactionDraftExtractionRepository repository =
            mock(TransactionDraftExtractionRepository.class);
    private final ConfirmedMerchantReferenceWriter merchantReferenceWriter =
            mock(ConfirmedMerchantReferenceWriter.class);
    private final FinancialTransactionWriter transactionWriter =
            mock(FinancialTransactionWriter.class);
    private final ExpenseConfirmationCommandHandler handler =
            new ExpenseConfirmationCommandHandler(
                    repository, merchantReferenceWriter, transactionWriter);

    @Test
    void confirmMarksExtractionUsedAndDraftConsumed() {
        TransactionDraftExtractionEntity extraction = activeExtraction();
        when(repository.findOwnedWhatsAppExtraction(5001L, "9198"))
                .thenReturn(Optional.of(extraction));

        handler.handle(new ExpenseConfirmationCommand(
                "9198", 5001L, ExpenseConfirmationCommand.Action.CONFIRM));

        assertThat(extraction.getStatus()).isEqualTo(TransactionDraftExtractionStatus.USED);
        assertThat(extraction.getDraft().getStatus()).isEqualTo(TransactionDraftStatus.CONSUMED);
        verify(merchantReferenceWriter).save(extraction);
        verify(transactionWriter).save(extraction, null);
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

    private TransactionDraftExtractionEntity activeExtraction() {
        TransactionDraftEntity draft = new TransactionDraftEntity();
        draft.setStatus(TransactionDraftStatus.PENDING);
        TransactionDraftExtractionEntity extraction = new TransactionDraftExtractionEntity();
        extraction.setDraft(draft);
        extraction.setStatus(TransactionDraftExtractionStatus.ACTIVE);
        return extraction;
    }
}
