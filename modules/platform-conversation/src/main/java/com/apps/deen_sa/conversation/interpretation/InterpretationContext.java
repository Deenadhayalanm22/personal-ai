package com.apps.deen_sa.conversation.interpretation;

import java.util.List;
import java.util.Map;

public record InterpretationContext(
        Long userId,
        String timezone,
        String currency,
        String lastQuestion,
        List<PendingEvent> pendingEvents,
        List<ConversationTurn> recentTurns,
        Map<String, Object> extensionContext
) { }
