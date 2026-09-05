package com.apps.deen_sa.conversation;

import com.apps.deen_sa.conversation.interpretation.UnifiedConversationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class DefaultConversationChannelGateway implements ConversationChannelGateway {
    private final AppUserService users;
    private final ConversationSessionService sessions;
    private final UnifiedConversationEngine engine;
    private final ConversationDiagnosticService diagnostics;

    @Override public SpeechResult process(String channel, String externalUserId, String messageId, String text) {
        Context context = context(channel, externalUserId, messageId);
        SpeechResult result = engine.process(text, context.value());
        sessions.save(context.value());
        diagnostics.record("MESSAGE", externalUserId, messageId, text, context.value(), result);
        return result;
    }

    @Override public SpeechResult processTrustedAnswer(String channel, String externalUserId, String messageId, String answer) {
        Context context = context(channel, externalUserId, messageId);
        SpeechResult result = engine.processTrustedAnswer(answer, context.value());
        sessions.save(context.value());
        diagnostics.record("TRUSTED_ANSWER", externalUserId, messageId, answer, context.value(), result);
        return result;
    }

    private Context context(String channel, String externalUserId, String messageId) {
        AppUserEntity user = users.resolve(channel, externalUserId);
        ConversationContext value = sessions.load(user.getId(), channel);
        value.setTimezone(user.getTimezone()); value.setLocale(user.getLocale()); value.setCurrency(user.getCurrency());
        var metadata = value.getMetadata() == null ? new HashMap<String, Object>() : new HashMap<>(value.getMetadata());
        if (messageId != null) metadata.put("inboundMessageId", messageId);
        metadata.put("tenantId", user.getId());
        value.setMetadata(metadata);
        return new Context(value);
    }

    private record Context(ConversationContext value) { }
}
