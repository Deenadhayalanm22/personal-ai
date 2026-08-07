package com.apps.deen_sa.extension.api;
import java.util.List;
import java.util.Map;
public interface ExtensionEvent {
    String eventId(); String eventType(); Map<String, Object> facts();
    List<String> unresolvedFields(); List<String> ambiguities(); List<? extends FactEvidence> evidence();
}
