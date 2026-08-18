package com.apps.deen_sa.extension.api;
import java.util.Map;
public interface CapabilityContext {
    Long getSessionId(); Long getUserId(); String getChannel(); String getTimezone(); String getLocale(); String getCurrency();
    String getActiveIntent(); void setActiveIntent(String value);
    String getWaitingForField(); void setWaitingForField(String value);
    Object getPartialObject(); void setPartialObject(Object value);
    Map<String, Object> getMetadata(); boolean isInFollowup(); void reset();
}
