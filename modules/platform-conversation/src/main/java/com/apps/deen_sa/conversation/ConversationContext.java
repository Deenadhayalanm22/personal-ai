package com.apps.deen_sa.conversation;

import lombok.Data;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.apps.deen_sa.conversation.interpretation.PendingEvent;
import com.apps.deen_sa.conversation.interpretation.ConversationTurn;

@Data
public class ConversationContext implements com.apps.deen_sa.extension.api.CapabilityContext {

    private Long sessionId;
    private Long userId = 1L;
    private String channel = "REST";
    private String timezone = "Asia/Kolkata";
    private String locale = "en-IN";
    private String currency = "INR";

    private Long activeTransactionId;

    // Namespaced capability currently owning the conversation.
    private String activeIntent;

    // For multi-turn follow-up:
    // Extension-defined field identifier.
    private String waitingForField;

    // The partial DTO that needs to be completed
    private Object partialObject;

    // Arbitrary metadata (if handler needs anything extra)
    private Map<String, Object> metadata;
    private List<PendingEvent> pendingEvents = new ArrayList<>();
    private List<ConversationTurn> recentTurns = new ArrayList<>();
    private String lastQuestion;
    private String interpreterVersion;

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
        this.pendingEvents.clear();
        this.lastQuestion = null;
    }
}
