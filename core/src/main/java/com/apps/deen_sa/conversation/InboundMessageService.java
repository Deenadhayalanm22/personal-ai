package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InboundMessageService {
    private final InboundMessageRepository repository;

    @Transactional
    public Long claim(String channel, String externalMessageId, String externalUserId) {
        if (externalMessageId == null || externalMessageId.isBlank()) return null;
        if (repository.existsByChannelAndExternalMessageId(channel, externalMessageId)) return null;
        InboundMessageEntity message = new InboundMessageEntity();
        message.setChannel(channel);
        message.setExternalMessageId(externalMessageId);
        message.setExternalUserId(externalUserId);
        message.setStatus("PROCESSING");
        try {
            return repository.saveAndFlush(message).getId();
        } catch (DataIntegrityViolationException duplicate) {
            return null;
        }
    }

    @Transactional
    public void complete(Long id) {
        if (id == null) return;
        repository.findById(id).ifPresent(message -> {
            message.setStatus("PROCESSED");
            message.setProcessedAt(Instant.now());
            repository.save(message);
        });
    }

    @Transactional
    public void fail(Long id) {
        if (id == null) return;
        repository.findById(id).ifPresent(message -> {
            message.setStatus("FAILED");
            message.setProcessedAt(Instant.now());
            repository.save(message);
        });
    }
}
