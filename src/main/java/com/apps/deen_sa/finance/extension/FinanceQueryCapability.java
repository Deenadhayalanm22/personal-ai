package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.extension.api.*;
import com.apps.deen_sa.finance.query.QueryHandler;

import java.util.Set;

final class FinanceQueryCapability implements QueryCapability {
    private final QueryHandler handler;
    FinanceQueryCapability(QueryHandler handler) { this.handler = handler; }
    @Override public String queryType() { return "QUERY"; }
    @Override public Set<String> periods() { return Set.of("TODAY", "THIS_WEEK", "THIS_MONTH", "THIS_YEAR",
            "LAST_MONTH", "LAST_7_DAYS", "LAST_3_MONTHS", "ACCOUNT_BALANCE", "CURRENT_STATUS", "UPCOMING_DUE"); }
    @Override public CapabilityResult handle(String period, CapabilityContext capabilityContext) {
        return handle(period, "", capabilityContext);
    }
    @Override public CapabilityResult handle(String period, String rawText, CapabilityContext capabilityContext) {
        return handle(period, rawText, null, null, capabilityContext);
    }
    @Override public CapabilityResult handle(String period, String rawText, String analysisIntent,
                                             String presentationMood, CapabilityContext capabilityContext) {
        if (!(capabilityContext instanceof ConversationContext context))
            throw new IllegalArgumentException("Finance compatibility adapter requires the host conversation bridge");
        SpeechResult result = handler.handleInterpreted(period, analysisIntent, presentationMood, context);
        return new CapabilityResult(result.getStatus().name(), result.getMessage(), Boolean.TRUE.equals(result.getNeedFollowup()),
                result.getMissingFields(), result.getPartial(), result.getSavedEntity(), java.util.List.of(), result.getMedia());
    }
}
