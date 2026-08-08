package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;

/** Handler contract for domains that consume the unified model's typed event directly. */
public interface StructuredEventHandler extends SpeechHandler {
    SpeechResult handleInterpreted(EventPatch event, String rawText, ConversationContext context);
}
