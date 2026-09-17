package com.apps.deen_sa.service;

import com.apps.deen_sa.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.dto.RecordedExpense;
import com.apps.deen_sa.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.domain.TransactionDraftStatus;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** FIN-EPIC-001 — Conversational expense capture. See docs/jira/personal-expense/FIN-EPIC-001-capture.md. */
@Service
public class ExpenseConfirmationCommandHandler {
    private final TransactionDraftExtractionRepository extractionRepository;
    private final ConfirmedReferenceWriter referenceWriter;
    private final FinancialTransactionWriter transactionWriter;
    private final MissingTransactionDateContextService dateContexts;
    private final MonthlyFinancialSnapshotService snapshots;

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
            var merchant = referenceWriter.save(
                    extraction, UserReferenceEntityType.MERCHANT, extraction.getMerchantName());
            var sourceAccount = referenceWriter.save(
                    extraction, UserReferenceEntityType.ACCOUNT, extraction.getSourceAccountName());
            transactionWriter.save(extraction, merchant, sourceAccount);
            if (snapshots != null) snapshots.refreshCurrent(draft.getUser());
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

    /** Compatibility constructor retained for the focused confirmation tests. */
    public ExpenseConfirmationCommandHandler(TransactionDraftExtractionRepository extractionRepository, ConfirmedReferenceWriter referenceWriter, FinancialTransactionWriter transactionWriter, MissingTransactionDateContextService dateContexts) { this(extractionRepository, referenceWriter, transactionWriter, dateContexts, null); }
    @Autowired
    public ExpenseConfirmationCommandHandler(TransactionDraftExtractionRepository extractionRepository, ConfirmedReferenceWriter referenceWriter, FinancialTransactionWriter transactionWriter, MissingTransactionDateContextService dateContexts, MonthlyFinancialSnapshotService snapshots) { this.extractionRepository = extractionRepository; this.referenceWriter = referenceWriter; this.transactionWriter = transactionWriter; this.dateContexts = dateContexts; this.snapshots = snapshots; }

    private void requireActive(TransactionDraftExtractionEntity extraction) {
        if (extraction.getStatus() != TransactionDraftExtractionStatus.ACTIVE) {
            throw new IllegalStateException("Only the ACTIVE extraction can be acted upon");
        }
    }
}
