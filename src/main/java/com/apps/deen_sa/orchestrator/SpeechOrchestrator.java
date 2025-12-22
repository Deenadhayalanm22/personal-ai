package com.apps.deen_sa.orchestrator;

import com.apps.deen_sa.dto.IntentResult;
import com.apps.deen_sa.llm.impl.IntentClassifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SpeechOrchestrator {

    private final IntentClassifier intentClassifier;
    private final Map<String, SpeechHandler> handlers;

    public SpeechOrchestrator(
            IntentClassifier classifier,
            List<SpeechHandler> handlerList) {
        this.intentClassifier = classifier;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(SpeechHandler::intentType, h -> h));
    }

    public SpeechResult process(String text, ConversationContext ctx) {

        // 1️⃣ CASE: Follow-up mode
        if (ctx.isInFollowup()) {
            SpeechHandler handler = handlers.get(ctx.getActiveIntent());
            if (handler == null) {
                ctx.reset();
                return SpeechResult.invalid("Internal error: No handler found for follow-up.");
            }
            return handler.handleFollowup(text, ctx);
        }

        // 2️⃣ CASE: New message → classify intent
        IntentResult intent = intentClassifier.classify(text);

        if (intent.confidence() < 0.5)
            return SpeechResult.unknown("Couldn't determine intent");

        SpeechHandler handler = handlers.get(intent.intent());
        if (handler == null)
            return SpeechResult.unknown("No handler found for " + intent.intent());

        // 3️⃣ Pass initial speech to the correct handler
        return handler.handleSpeech(text, ctx);
    }
}
