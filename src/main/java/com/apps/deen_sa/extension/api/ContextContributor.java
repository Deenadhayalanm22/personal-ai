package com.apps.deen_sa.extension.api;

import java.util.Map;

public interface ContextContributor {
    String namespace();
    Object contribute(Long tenantId, Long userId);

    default Map.Entry<String, Object> entry(Long tenantId, Long userId) {
        return Map.entry(namespace(), contribute(tenantId, userId));
    }
}
