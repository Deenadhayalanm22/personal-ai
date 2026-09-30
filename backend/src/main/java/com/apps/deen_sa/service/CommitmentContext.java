package com.apps.deen_sa.service;

import java.util.*;

/** Mutable only while the enrichment pipeline is assembling verified facts. */
public final class CommitmentContext {
    private final Map<String, Object> facts = new LinkedHashMap<>();
    private final List<CommitmentInsight> insights = new ArrayList<>();

    CommitmentContext(Map<String, Object> coreFacts) { facts.putAll(coreFacts); }
    public void put(String key, Object value) { if (value != null) facts.put(key, value); }
    public <T> Optional<T> fact(String key, Class<T> type) {
        Object value = facts.get(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
    public void addInsight(CommitmentInsight insight) { insights.add(insight); }
    public Map<String, Object> facts() { return Collections.unmodifiableMap(facts); }
    public List<CommitmentInsight> insights() { return List.copyOf(insights); }
}
