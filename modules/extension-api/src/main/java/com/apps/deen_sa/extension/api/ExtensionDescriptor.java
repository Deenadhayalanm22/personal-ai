package com.apps.deen_sa.extension.api;

import java.util.List;
import java.util.Set;

public record ExtensionDescriptor(
        String id,
        String version,
        int apiVersion,
        String displayName,
        boolean installForNewTenant,
        Set<String> supportedLocales,
        List<String> routingExamples
) {
    public ExtensionDescriptor {
        if (id == null || !id.matches("[a-z][a-z0-9-]+")) {
            throw new IllegalArgumentException("Extension id must be a lowercase namespace");
        }
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Extension version is required");
        supportedLocales = supportedLocales == null ? Set.of() : Set.copyOf(supportedLocales);
        routingExamples = routingExamples == null ? List.of() : List.copyOf(routingExamples);
    }
}
