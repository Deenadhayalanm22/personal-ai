package com.apps.deen_sa.conversation.interpretation;

public interface ConversationInterpreter {
    TurnInterpretation interpret(String userMessage, InterpretationContext context);
}
