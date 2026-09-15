package com.apps.deen_sa.service;

import com.apps.deen_sa.dto.NormalizedExpense;
import com.apps.deen_sa.dto.StoredDraftExtraction;
import com.apps.deen_sa.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.domain.TransactionDraftStatus;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import com.apps.deen_sa.repository.TransactionDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TransactionDraftExtractionWriter {
    private final TransactionDraftRepository draftRepository;
    private final TransactionDraftExtractionRepository extractionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredDraftExtraction saveActive(NormalizedExpense normalized) {
        TransactionDraftEntity draft = draftRepository.findByIdForUpdate(normalized.draftId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction draft not found: " + normalized.draftId()));
        if (draft.getStatus() != TransactionDraftStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING draft can be extracted");
        }

        if (extractionRepository.findByDraftIdAndStatus(
                draft.getId(), TransactionDraftExtractionStatus.ACTIVE).isPresent()) {
            throw new IllegalStateException("A draft can have only one extraction attempt");
        }

        TransactionDraftExtractionEntity extraction = new TransactionDraftExtractionEntity();
        extraction.setDraft(draft);
        extraction.setAmount(normalized.amount());
        extraction.setMerchantName(normalized.merchant());
        extraction.setSourceAccountName(normalized.sourceAccount());
        extraction.setCategoryId(normalized.category());
        extraction.setSubcategoryId(normalized.subcategory());
        extraction.setOccurredAt(normalized.transactionDate());
        extraction.setStatus(TransactionDraftExtractionStatus.ACTIVE);
        extraction.setConfidence(normalized.confidence());

        return toStored(extractionRepository.saveAndFlush(extraction));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelWithoutExtraction(Long draftId) {
        TransactionDraftEntity draft = draftRepository.findByIdForUpdate(draftId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction draft not found: " + draftId));
        if (draft.getStatus() == TransactionDraftStatus.PENDING) {
            draft.setStatus(TransactionDraftStatus.CANCELLED);
            draft.setUpdatedAt(Instant.now());
        }
    }

    private StoredDraftExtraction toStored(TransactionDraftExtractionEntity extraction) {
        return new StoredDraftExtraction(
                extraction.getId(),
                extraction.getDraft().getId(),
                extraction.getDraft().getUser().getExternalUserId(),
                extraction.getAmount(),
                extraction.getMerchantName(),
                extraction.getSourceAccountName(),
                extraction.getCategoryId(),
                extraction.getSubcategoryId(),
                extraction.getOccurredAt(),
                extraction.getConfidence());
    }
}
