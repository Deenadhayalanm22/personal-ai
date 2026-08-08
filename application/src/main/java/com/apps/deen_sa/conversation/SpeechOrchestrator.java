package com.apps.deen_sa.conversation;

import com.apps.deen_sa.conversation.interpretation.UnifiedConversationEngine;
import org.springframework.stereotype.Service;

/** Channel-neutral compatibility facade over the extension-driven conversation engine. */
@Service
public class SpeechOrchestrator {
    private final UnifiedConversationEngine engine;

    public SpeechOrchestrator(UnifiedConversationEngine engine) { this.engine = engine; }

    public SpeechResult process(String text, ConversationContext context) {
        return engine.process(text, context);
    }
}
