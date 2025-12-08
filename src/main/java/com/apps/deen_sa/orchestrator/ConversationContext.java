package com.apps.deen_sa.orchestrator;

import lombok.Data;
import java.util.Map;

@Data
public class ConversationContext {

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
        this.activeIntent = null;
        this.waitingForField = null;
        this.partialObject = null;
        if (metadata != null) metadata.clear();
    }
}
