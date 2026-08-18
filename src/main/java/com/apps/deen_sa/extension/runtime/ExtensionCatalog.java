package com.apps.deen_sa.extension.runtime;

import com.apps.deen_sa.extension.api.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExtensionCatalog {
    public static final int SUPPORTED_API_VERSION = 1;
    private final Map<String, BusinessExtension> extensions;
    private final Map<String, EventCapability> events;
    private final Map<String, QueryCapability> queries;
    public ExtensionCatalog(List<BusinessExtension> discovered) {
        discovered.forEach(extension -> {
            if (extension.descriptor().apiVersion() != SUPPORTED_API_VERSION) {
                throw new IllegalStateException("Incompatible extension " + extension.descriptor().id()
                        + ": API " + extension.descriptor().apiVersion() + ", runtime " + SUPPORTED_API_VERSION);
            }
        });
        extensions = unique(discovered, extension -> extension.descriptor().id(), "extension");
        events = unique(discovered.stream().flatMap(e -> e.events().stream()).toList(),
                capability -> capability.eventType().toUpperCase(Locale.ROOT), "event capability");
        queries = unique(discovered.stream().flatMap(e -> e.queries().stream()).toList(),
                capability -> capability.queryType().toUpperCase(Locale.ROOT), "query capability");
    }

    public Optional<EventCapability> event(Long tenantId, String type) {
        EventCapability capability = events.get(normalize(type));
        return Optional.ofNullable(capability);
    }

    public Optional<QueryCapability> query(Long tenantId, String type) {
        QueryCapability capability = queries.get(normalize(type));
        return Optional.ofNullable(capability);
    }

    public Map<String, Object> context(Long tenantId, Long userId) {
        return extensions.values().stream()
                .flatMap(e -> e.contextContributors().stream()).map(c -> c.entry(tenantId, userId))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String help(Long tenantId, String locale) {
        return extensions.values().stream()
                .map(extension -> extension.help(locale)).filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    public Optional<EventCapability> routeDeterministically(Long tenantId, String text) {
        return extensions.values().stream()
                .flatMap(e -> e.deterministicRouters().stream()).map(router -> router.eventType(text))
                .flatMap(Optional::stream).findFirst().flatMap(type -> event(tenantId, type));
    }

    public List<DeterministicEventCandidate> extractDeterministically(Long tenantId, String text) {
        return extensions.values().stream()
                .flatMap(e -> e.deterministicRouters().stream()).flatMap(router -> router.events(text).stream())
                .filter(candidate -> event(tenantId, candidate.eventType()).isPresent()).toList();
    }

    public Optional<String> queryDeterministically(Long tenantId, String text) {
        return extensions.values().stream()
                .flatMap(e -> e.deterministicRouters().stream()).map(router -> router.query(text))
                .flatMap(Optional::stream).findFirst();
    }

    public String interpretationInstructions(Long tenantId) {
        return extensions.values().stream()
                .flatMap(e -> e.promptContributors().stream()).map(InterpretationPromptContributor::instructions)
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining("\n\n"));
    }

    public Collection<EventCapability> enabledEvents(Long tenantId) {
        return extensions.values().stream()
                .flatMap(e -> e.events().stream()).toList();
    }

    private static String normalize(String value) { return value == null ? "" : value.toUpperCase(Locale.ROOT); }

    private static <T> Map<String, T> unique(Collection<T> values, Function<T, String> key, String label) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = key.apply(value);
            if (result.putIfAbsent(id, value) != null) throw new IllegalStateException("Duplicate " + label + ": " + id);
        }
        return Map.copyOf(result);
    }
}
