package com.apps.deen_sa.extension.api;

import java.util.Set;
import java.util.Map;

public interface EventCapability {
    String eventType();
    String schemaVersion();
    Set<String> fields();
    default Map<String, String> fieldTypes() {
        return fields().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(value -> value, value -> "string"));
    }
    String extractionInstructions();
    CapabilityResult handle(ExtensionEvent event, String rawText, CapabilityContext context, boolean continuation);
}
