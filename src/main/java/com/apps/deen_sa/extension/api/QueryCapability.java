package com.apps.deen_sa.extension.api;

import java.util.Set;

public interface QueryCapability {
    String queryType();
    Set<String> periods();
    CapabilityResult handle(String period, CapabilityContext context);

    /**
     * Query text is presentation context, not part of the database contract.  The
     * default keeps third-party capabilities source compatible.
     */
    default CapabilityResult handle(String period, String rawText, CapabilityContext context) {
        return handle(period, context);
    }

}
