package com.apps.deen_sa.extension.api;
import com.apps.deen_sa.conversation.ResponseMedia;
import java.util.List;
public record CapabilityResult(String status, String message, boolean followup, List<String> missingFields,
                               Object partial, Object savedEntity, List<CapabilityAction> actions, ResponseMedia media) {
    public CapabilityResult {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
    public static CapabilityResult info(String message) { return new CapabilityResult("INFO", message, false, null, null, null, null, null); }
    public static CapabilityResult saved(String message, Object entity) { return new CapabilityResult("SAVED", message, false, null, null, entity, null, null); }
    public static CapabilityResult followup(String message, List<String> fields, Object partial) { return new CapabilityResult("FOLLOWUP", message, true, fields, partial, null, null, null); }
    public static CapabilityResult unknown(String message) { return new CapabilityResult("UNKNOWN", message, false, null, null, null, null, null); }
}
