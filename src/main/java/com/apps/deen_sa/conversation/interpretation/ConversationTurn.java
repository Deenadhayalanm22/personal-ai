package com.apps.deen_sa.conversation.interpretation;

import java.time.Instant;

public record ConversationTurn(String role, String text, Instant at) { }
