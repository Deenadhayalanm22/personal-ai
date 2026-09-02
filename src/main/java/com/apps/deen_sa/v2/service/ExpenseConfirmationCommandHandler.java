package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.v2.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.v2.dto.RecordedExpense;
import com.apps.deen_sa.v2.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.v2.domain.TransactionDraftStatus;
import com.apps.deen_sa.v2.entity.TransactionDraftEntity;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.v2.repository.TransactionDraftExtractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExpenseConfirmationCommandHandler {
    private final TransactionDraftExtractionRepository extractionRepository;
    private final ConfirmedMerchantReferenceWriter merchantReferenceWriter;
    private final FinancialTransactionWriter transactionWriter;
    private final V2MissingTransactionDateContextService dateContexts;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecordedExpense handle(ExpenseConfirmationCommand command) {
        TransactionDraftExtractionEntity extraction = extractionRepository
                .findOwnedWhatsAppExtraction(command.extractionId(), command.externalUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Extraction does not belong to this WhatsApp user"));
        TransactionDraftEntity draft = extraction.getDraft();

        if (command.action() == ExpenseConfirmationCommand.Action.CONFIRM) {
            if (extraction.getStatus() == TransactionDraftExtractionStatus.USED
                    && draft.getStatus() == TransactionDraftStatus.CONSUMED) {
                return null;
            }
            requireActive(extraction);
            extraction.setStatus(TransactionDraftExtractionStatus.USED);
            draft.setStatus(TransactionDraftStatus.CONSUMED);
            var merchant = merchantReferenceWriter.save(extraction);
            transactionWriter.save(extraction, merchant);
            dateContexts.consumeForConfirmedDraft(draft);
            draft.setUpdatedAt(Instant.now());
            return new RecordedExpense(
                    command.externalUserId(),
                    extraction.getAmount(),
                    extraction.getMerchantName());
        } else {
            if (extraction.getStatus() == TransactionDraftExtractionStatus.REJECTED
                    && draft.getStatus() == TransactionDraftStatus.CANCELLED) {
                return null;
            }
            requireActive(extraction);
            extraction.setStatus(TransactionDraftExtractionStatus.REJECTED);
            draft.setStatus(TransactionDraftStatus.CANCELLED);
        }
        draft.setUpdatedAt(Instant.now());
        return null;
    }

    private void requireActive(TransactionDraftExtractionEntity extraction) {
        if (extraction.getStatus() != TransactionDraftExtractionStatus.ACTIVE) {
            throw new IllegalStateException("Only the ACTIVE extraction can be acted upon");
        }
    }
}
