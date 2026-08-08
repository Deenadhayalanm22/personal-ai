package com.apps.deen_sa.conversation;

/** Channel-neutral application boundary used by transport adapters. */
public interface ConversationChannelGateway {
    SpeechResult process(String channel, String externalUserId, String messageId, String text);
    SpeechResult processTrustedAnswer(String channel, String externalUserId, String messageId, String answer);
}
