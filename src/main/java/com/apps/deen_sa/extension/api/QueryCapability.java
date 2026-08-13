package com.apps.deen_sa.extension.api;

import java.util.Set;

public interface QueryCapability {
    String queryType();
    Set<String> periods();
    CapabilityResult handle(String period, CapabilityContext context);
}
