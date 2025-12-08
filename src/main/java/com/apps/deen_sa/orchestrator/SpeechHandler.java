package com.apps.deen_sa.orchestrator;

public interface SpeechHandler {
    String intentType();                        // EXPENSE, INCOME, etc.
    SpeechResult handleSpeech(String text, ConversationContext ctx);
    SpeechResult handleFollowup(String userAnswer, ConversationContext ctx);
}
