package com.apps.deen_sa.finance.expense;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Component
public class ExpenseTaxonomyRegistry {

    private final Map<String, Set<String>> taxonomy = new HashMap<>();

    public ExpenseTaxonomyRegistry() {
        load();
    }

    private void load() {
        Yaml yaml = new Yaml();
        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("expense-taxonomy.yml");

        if (is == null) {
            throw new IllegalStateException("expense-taxonomy.yml not found in resources");
        }

        Map<String, Object> raw = yaml.load(is);

        raw.forEach((category, subcats) -> {
            taxonomy.put(
                    category,
                    new HashSet<>((List<String>) subcats)
            );
        });
    }

    public Set<String> categories() {
        return taxonomy.keySet();
    }

    public Set<String> subcategoriesFor(String category) {
        return taxonomy.getOrDefault(category, Set.of());
    }

    public boolean isCategory(String value) {
        return taxonomy.containsKey(value);
    }

    public boolean isSubcategory(String value) {
        return taxonomy.values().stream()
                .anyMatch(set -> set.contains(value));
    }

    public Set<String> allLabels() {
        Set<String> labels = new LinkedHashSet<>(taxonomy.keySet());
        taxonomy.values().forEach(labels::addAll);
        return Set.copyOf(labels);
    }

    public Optional<String> canonicalLabel(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return allLabels().stream().filter(label -> label.equalsIgnoreCase(value.trim())).findFirst();
    }

    public Optional<String> parentCategory(String subcategory) {
        if (subcategory == null) return Optional.empty();
        return taxonomy.entrySet().stream().filter(entry -> entry.getValue().stream()
                .anyMatch(value -> value.equalsIgnoreCase(subcategory.trim()))).map(Map.Entry::getKey).findFirst();
    }
}
