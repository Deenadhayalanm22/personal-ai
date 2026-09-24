package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.domain.TransactionDraftStatus;
import com.apps.deen_sa.domain.TransactionDraftExtractionStatus;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.repository.TransactionDraftRepository;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** FIN-EPIC-001 — Conversational expense capture. */
@Service
@RequiredArgsConstructor
public class AudioDraftReviewStore {
    private final TransactionDraftRepository drafts;
    private final TransactionDraftExtractionRepository extractions;

    @Transactional
    public boolean stage(Long draftId, String transcript) {
        TransactionDraftEntity draft = drafts.findByIdForUpdate(draftId).orElseThrow();
        if (draft.getInputType() != InputType.AUDIO || draft.getStatus() != TransactionDraftStatus.PENDING) return false;
        draft.setTranscribedText(transcript);
        draft.setStatus(TransactionDraftStatus.TRANSCRIPT_REVIEW);
        draft.setUpdatedAt(Instant.now());
        return true;
    }

    @Transactional
    public ApprovedTranscript review(Long draftId, String externalUserId, boolean approve) {
        TransactionDraftEntity draft = drafts.findByIdForUpdate(draftId).orElseThrow();
        if (draft.getInputType() != InputType.AUDIO || draft.getSource() != MessageSource.WHATSAPP
                || !draft.getUser().getExternalUserId().equals(externalUserId)) {
            throw new IllegalArgumentException("Audio draft does not belong to this WhatsApp user");
        }
        if (draft.getStatus() == TransactionDraftStatus.PENDING && approve
                && draft.getTranscribedText() != null
                && extractions.findByDraftIdAndStatus(draftId, TransactionDraftExtractionStatus.ACTIVE).isEmpty()) {
            return new ApprovedTranscript(draft.getId(), draft.getSourceMessageId(), draft.getTranscribedText());
        }
        if (draft.getStatus() != TransactionDraftStatus.TRANSCRIPT_REVIEW) return null;
        draft.setStatus(approve ? TransactionDraftStatus.PENDING : TransactionDraftStatus.CANCELLED);
        draft.setUpdatedAt(Instant.now());
        return approve ? new ApprovedTranscript(draft.getId(), draft.getSourceMessageId(),
                draft.getTranscribedText()) : null;
    }

    public record ApprovedTranscript(Long draftId, String sourceMessageId, String text) { }
}
