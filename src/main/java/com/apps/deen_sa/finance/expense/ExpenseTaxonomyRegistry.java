package com.apps.deen_sa.finance.expense;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Component
public class ExpenseTaxonomyRegistry {

    private final Map<String, Set<String>> taxonomy = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public ExpenseTaxonomyRegistry() {
        load();
        loadAliases();
    }

    private void loadAliases() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("expense-taxonomy-aliases.yml");
        if (is == null) return;
        Map<String, String> raw = new Yaml().load(is);
        if (raw == null) return;
        raw.forEach((alias, label) -> canonicalLabel(label).ifPresent(canonical ->
                aliases.put(alias.trim().toLowerCase(Locale.ROOT), canonical)));
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

    public Optional<String> canonicalAlias(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Optional.ofNullable(aliases.get(value.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<String> canonicalAliasInText(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return aliases.entrySet().stream()
                .filter(entry -> (" " + normalized + " ").contains(" " + entry.getKey() + " "))
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length)).reversed())
                .map(Map.Entry::getValue).findFirst();
    }

    public Optional<String> parentCategory(String subcategory) {
        if (subcategory == null) return Optional.empty();
        return taxonomy.entrySet().stream().filter(entry -> entry.getValue().stream()
                .anyMatch(value -> value.equalsIgnoreCase(subcategory.trim()))).map(Map.Entry::getKey).findFirst();
    }
}
