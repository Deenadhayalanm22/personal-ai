package com.apps.deen_sa.conversation;

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

        String normalized = text == null ? "" : text.trim().toLowerCase();
        if (ctx.isInFollowup() && (normalized.equals("skip") || normalized.equals("later")
                || normalized.equals("not sure") || normalized.equals("don't know"))) {
            ctx.reset();
            return SpeechResult.info("No problem — I saved what you told me. You can add the missing detail later.");
        }
        if (ctx.isInFollowup() && (normalized.equals("cancel") || normalized.equals("stop"))) {
            ctx.reset();
            return SpeechResult.info("Okay — I stopped the questions. Any activity already recorded is still saved.");
        }

        if (ctx.isInFollowup() && looksLikeNewActivity(normalized)) {
            IntentResult interruption = intentClassifier.classify(text);
            SpeechHandler newHandler = interruption.confidence() >= 0.75
                    ? handlers.get(interruption.intent()) : null;
            if (newHandler != null && !interruption.intent().equals(ctx.getActiveIntent())) {
                ctx.reset();
                return newHandler.handleSpeech(text, ctx);
            }
            if (newHandler != null && containsTransactionVerb(normalized)) {
                ctx.reset();
                return newHandler.handleSpeech(text, ctx);
            }
        }

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

    private boolean looksLikeNewActivity(String text) {
        return containsTransactionVerb(text)
                || text.startsWith("how much")
                || text.startsWith("show me")
                || text.startsWith("what did");
    }

    private boolean containsTransactionVerb(String text) {
        return List.of("spent ", "paid ", "bought ", "sold ", "received ", "got ",
                        "transferred ", "invested ", "setup ", "set up ", "i have ")
                .stream().anyMatch(text::contains);
    }
}
