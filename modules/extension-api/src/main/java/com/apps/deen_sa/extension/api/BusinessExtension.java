package com.apps.deen_sa.extension.api;

import java.util.Collection;

/** Public SPI. Implementations own domain vocabulary, rules, persistence and presentation. */
public interface BusinessExtension {
    ExtensionDescriptor descriptor();
    Collection<EventCapability> events();
    default Collection<QueryCapability> queries() { return java.util.List.of(); }
    default Collection<ContextContributor> contextContributors() { return java.util.List.of(); }
    default Collection<DeterministicEventRouter> deterministicRouters() { return java.util.List.of(); }
    default Collection<InterpretationPromptContributor> promptContributors() { return java.util.List.of(); }
}
