package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.SpendingNature;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Component
public class ExpenseTaxonomyRegistry {

    private final Map<String, Set<String>> taxonomy = new LinkedHashMap<>();
    private final Map<Classification, SpendingNature> spendingNatures = new HashMap<>();
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

        raw.forEach((category, value) -> {
            if (!(value instanceof List<?> subcategories)) {
                throw new IllegalStateException("Taxonomy category must contain a list: " + category);
            }

            Set<String> names = new LinkedHashSet<>();
            for (Object entry : subcategories) {
                if (!(entry instanceof Map<?, ?> definition)) {
                    throw new IllegalStateException("Taxonomy subcategory must be an object under: " + category);
                }
                String name = requiredText(definition, "name", category);
                String nature = requiredText(definition, "spendingNature", category + " / " + name);
                SpendingNature spendingNature;
                try {
                    spendingNature = SpendingNature.valueOf(nature.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Invalid spendingNature for " + category + " / " + name, exception);
                }
                if (!names.add(name)) {
                    throw new IllegalStateException("Duplicate subcategory under " + category + ": " + name);
                }
                spendingNatures.put(new Classification(category, name), spendingNature);
            }
            taxonomy.put(category, names);
        });
    }

    private String requiredText(Map<?, ?> definition, String key, String location) {
        Object value = definition.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Missing " + key + " in expense taxonomy at " + location);
        }
        return text.trim();
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

    public Optional<SpendingNature> spendingNatureFor(String category, String subcategory) {
        if (category == null || subcategory == null) return Optional.empty();
        return spendingNatures.entrySet().stream()
                .filter(entry -> entry.getKey().category().equalsIgnoreCase(category.trim())
                        && entry.getKey().subcategory().equalsIgnoreCase(subcategory.trim()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private record Classification(String category, String subcategory) { }
}
