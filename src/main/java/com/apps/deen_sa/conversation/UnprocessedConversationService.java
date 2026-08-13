package com.apps.deen_sa.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UnprocessedConversationService {
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private final UnprocessedConversationMessageRepository repository;

    @Transactional
    public void record(String text, String reason, ConversationContext context) {
        if (text == null || text.isBlank() || context == null || context.getUserId() == null) return;
        String channel = context.getChannel() == null ? "UNKNOWN" : context.getChannel().toUpperCase(java.util.Locale.ROOT);
        String externalId = metadata(context, "inboundMessageId");
        UnprocessedConversationMessageEntity value = externalId == null ? new UnprocessedConversationMessageEntity()
                : repository.findByChannelAndExternalMessageId(channel, externalId).orElseGet(UnprocessedConversationMessageEntity::new);
        Instant now = Instant.now();
        if (value.getId() == null) {
            value.setTenantId(tenantId(context)); value.setUserId(context.getUserId()); value.setChannel(channel);
            value.setExternalMessageId(externalId); value.setCreatedAt(now); value.setOccurrenceCount(1);
        } else value.setOccurrenceCount(value.getOccurrenceCount() + 1);
        value.setMessageText(text.substring(0, Math.min(text.length(), MAX_MESSAGE_LENGTH)));
        value.setLocale(context.getLocale()); value.setReason(reason); value.setInterpreterVersion(context.getInterpreterVersion());
        value.setStatus("NEW"); value.setUpdatedAt(now); repository.save(value);
    }

    private Long tenantId(ConversationContext context) {
        String value = metadata(context, "tenantId");
        try { return value == null ? context.getUserId() : Long.valueOf(value); }
        catch (NumberFormatException ignored) { return context.getUserId(); }
    }
    private String metadata(ConversationContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? null : value.toString();
    }
}
