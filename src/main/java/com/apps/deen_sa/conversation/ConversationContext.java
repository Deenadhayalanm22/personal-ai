package com.apps.deen_sa.conversation;

import lombok.Data;
import java.util.Map;

@Data
public class ConversationContext {

    private Long sessionId;
    private Long userId = 1L;
    private String channel = "REST";

    private Long activeTransactionId;

    // Which handler currently owns the conversation?
    // e.g., "EXPENSE", "ACCOUNT_TRANSFER", "INVESTMENT"
    private String activeIntent;

    // For multi-turn follow-up:
    // e.g., ["category"], ["paymentMethod"], ["accountSource"]
    private String waitingForField;

    // The partial DTO that needs to be completed
    private Object partialObject;

    // Arbitrary metadata (if handler needs anything extra)
    private Map<String, Object> metadata;

    // Determine if user is in a multi-step follow-up flow
    public boolean isInFollowup() {
        return activeIntent != null && waitingForField != null;
    }

    // Reset context fully
    public void reset() {
        this.activeTransactionId = null;
        this.activeIntent = null;
        this.waitingForField = null;
        this.partialObject = null;
        if (metadata != null) metadata.clear();
    }
}
