package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.insights.WebExpenseCaptureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** FIN-EPIC-001: retain browser evidence and a server-owned confirmation preview. */
@Service
@RequiredArgsConstructor
public class WebExpenseCaptureStore {
    private final TransactionDraftRepository drafts;
    private final TransactionDraftExtractionRepository extractions;

    @Transactional
    public Long save(AppUserEntity user, WebExpenseCaptureService.Request request,
                     WebExpenseCaptureService.Preview preview, String evidence, boolean ready) {
        String sourceId = user.getId() + ":" + request.requestId();
        drafts.insertPendingIfAbsent(user.getId(), "TEXT", "WEB_APP", sourceId, evidence);
        var existing = drafts.findBySourceAndSourceMessageId(MessageSource.WEB_APP, sourceId).orElseThrow();
        var draft = drafts.findByIdForUpdate(existing.getId()).orElseThrow();
        var active = extractions.findByDraftIdAndStatus(draft.getId(), TransactionDraftExtractionStatus.ACTIVE);
        if (active.isPresent()) return active.get().getId();
        if (!ready) {
            draft.setStatus(TransactionDraftStatus.CANCELLED);
            draft.setUpdatedAt(Instant.now());
            return null;
        }
        if (draft.getStatus() != TransactionDraftStatus.PENDING) throw new IllegalStateException("Draft closed");
        var extraction = new TransactionDraftExtractionEntity();
        extraction.setDraft(draft);
        extraction.setAmount(preview.amount());
        extraction.setOccurredAt(preview.date());
        extraction.setCategoryId(preview.category());
        extraction.setSubcategoryId(preview.subcategory());
        extraction.setMerchantName(preview.merchant());
        extraction.setSourceAccountName(preview.account());
        extraction.setStatus(TransactionDraftExtractionStatus.ACTIVE);
        return extractions.saveAndFlush(extraction).getId();
    }
}
