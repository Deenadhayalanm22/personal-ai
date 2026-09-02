package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.conversation.AppUserService;
import com.apps.deen_sa.v2.dto.DraftWriteResult;
import com.apps.deen_sa.v2.dto.InboundMessage;
import com.apps.deen_sa.v2.domain.TransactionDraftStatus;
import com.apps.deen_sa.v2.entity.TransactionDraftEntity;
import com.apps.deen_sa.v2.repository.TransactionDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionDraftWriter {
    private final TransactionDraftRepository repository;
    private final AppUserService appUserService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DraftWriteResult routeAndCommit(InboundMessage message) {
        return repository.findBySourceAndSourceMessageId(message.source(), message.sourceMessageId())
                .map(existing -> new DraftWriteResult(existing.getId(), false))
                .orElseGet(() -> pendingDraft(message)
                        .map(existing -> new DraftWriteResult(existing.getId(), false))
                        .orElseGet(() -> insertAndLoad(message)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DraftWriteResult saveAndCommit(InboundMessage message) {
        return routeAndCommit(message);
    }

    private java.util.Optional<TransactionDraftEntity> pendingDraft(InboundMessage message) {
        return repository
                .findFirstByUserExternalUserIdAndUserChannelAndSourceAndStatusOrderByCreatedAtDesc(
                        message.externalUserId(),
                        message.source().name(),
                        message.source(),
                        TransactionDraftStatus.PENDING);
    }

    private DraftWriteResult insertAndLoad(InboundMessage message) {
        AppUserEntity user = appUserService.resolve(message.source().name(), message.externalUserId());

        int inserted = repository.insertPendingIfAbsent(
                user.getId(),
                message.inputType().name(),
                message.source().name(),
                message.sourceMessageId(),
                message.rawContent());

        TransactionDraftEntity persisted = repository
                .findBySourceAndSourceMessageId(message.source(), message.sourceMessageId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction draft was not available after insert"));
        return new DraftWriteResult(persisted.getId(), inserted == 1);
    }
}
