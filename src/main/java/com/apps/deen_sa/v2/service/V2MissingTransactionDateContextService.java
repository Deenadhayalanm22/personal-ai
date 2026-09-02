package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.context.PendingActionContextRepository;
import com.apps.deen_sa.conversation.context.PendingActionContextService;
import com.apps.deen_sa.conversation.context.PendingActionContextStatus;
import com.apps.deen_sa.v2.repository.TransactionDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class V2MissingTransactionDateContextService {
    private final TransactionDraftRepository drafts;
    private final PendingActionContextRepository contexts;
    private final PendingActionContextService contextRules;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LocalDate applyToDraft(
            long draftId,
            String rawText,
            LocalDate extractedDate
    ) {
        if (contextRules.hasExplicitTemporalText(rawText)) {
            return extractedDate;
        }
        var draft = drafts.findByIdForUpdate(draftId).orElseThrow();
        return contexts.findFirstByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        draft.getUser().getId(), PendingActionContextStatus.ACTIVE,
                        clock.instant())
                .filter(context -> PendingActionContextService.TYPE.equals(
                        context.getContextType()))
                .map(context -> {
                    draft.setPendingActionContextId(context.getId());
                    return LocalDate.parse(context.getContextValue());
                })
                .orElse(extractedDate);
    }

    public void consumeForConfirmedDraft(
            com.apps.deen_sa.v2.entity.TransactionDraftEntity draft
    ) {
        contextRules.consumeIfActive(
                draft.getUser().getId(), draft.getPendingActionContextId());
    }
}
