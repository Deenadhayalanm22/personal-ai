package com.apps.deen_sa.service;

import java.util.*;

/** Mutable only while the enrichment pipeline is assembling verified facts. */
public final class StoryContext {
    private final Map<String, Object> facts = new LinkedHashMap<>();
    private final List<StoryInsight> insights = new ArrayList<>();

    StoryContext(Map<String, Object> coreFacts) { facts.putAll(coreFacts); }
    public void put(String key, Object value) { if (value != null) facts.put(key, value); }
    public <T> Optional<T> fact(String key, Class<T> type) {
        Object value = facts.get(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
    public void addInsight(StoryInsight insight) { insights.add(insight); }
    public Map<String, Object> facts() { return Collections.unmodifiableMap(facts); }
    public List<StoryInsight> insights() { return List.copyOf(insights); }
}
