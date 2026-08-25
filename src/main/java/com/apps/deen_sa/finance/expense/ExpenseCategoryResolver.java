package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.llm.impl.TaxonomySemanticMatcher;
import org.springframework.stereotype.Component;
import java.util.*;

/** Resolves free text to configured taxonomy nodes; it never invents or hard-codes a category. */
@Component
public class ExpenseCategoryResolver {
    private final ExpenseTaxonomyRegistry taxonomy;
    private final TaxonomySemanticMatcher semanticMatcher;
    public ExpenseCategoryResolver(ExpenseTaxonomyRegistry taxonomy, TaxonomySemanticMatcher semanticMatcher) {
        this.taxonomy = taxonomy; this.semanticMatcher = semanticMatcher;
    }

    public void canonicalize(ExpenseDto expense, String originalText) {
        String subcategory = taxonomy.canonicalLabel(expense.getSubcategory()).orElse(null);
        String category = taxonomy.canonicalLabel(expense.getCategory()).orElse(null);
        String evidence = firstMeaningful(expense.getMerchantName(), originalText);
        String evidencedAlias = taxonomy.canonicalAlias(evidence)
                .or(() -> taxonomy.canonicalAliasInText(evidence)).orElse(null);
        if (evidencedAlias != null) {
            Optional<String> evidencedParent = taxonomy.parentCategory(evidencedAlias);
            if (evidencedParent.isPresent() && !evidencedParent.get().equals(category)) {
                expense.setCategory(evidencedParent.get());
                expense.setSubcategory(evidencedAlias);
                return;
            }
        }
        // An unfamiliar but identifiable physical product is still Shopping, but it must not
        // be forced into an inaccurate specific bucket. Its proposed reusable classification
        // is captured separately for taxonomy review.
        if ("Miscellaneous".equals(category) && isPhysicalPurchase(originalText)) {
            expense.setCategory("Shopping");
            expense.setSubcategory("Other Shopping");
            return;
        }
        if (subcategory != null && taxonomy.parentCategory(subcategory).isPresent()) {
            String parent = taxonomy.parentCategory(subcategory).orElseThrow();
            String raw = evidence;
            String explicitAlias = taxonomy.canonicalAlias(raw)
                    .or(() -> taxonomy.canonicalAliasInText(raw))
                    .filter(taxonomy.subcategoriesFor(parent)::contains)
                    .orElse(null);
            // Once a valid pair exists, keep it stable. Only deterministic aliases
            // may refine it; repeated semantic-model calls would reclassify the
            // same transaction differently across requests.
            boolean alreadyConsistent = parent.equals(category);
            expense.setSubcategory(explicitAlias != null ? explicitAlias
                    : alreadyConsistent ? subcategory
                    : resolveWithinCategory(parent, raw).orElse(subcategory));
            expense.setCategory(parent);
            return;
        }
        if (category != null) {
            Optional<String> parent = taxonomy.parentCategory(category);
            if (parent.isPresent()) { expense.setCategory(parent.get()); expense.setSubcategory(category); }
            else {
                expense.setCategory(category);
                expense.setSubcategory(resolveWithinCategory(category,
                        firstMeaningful(expense.getMerchantName(), originalText)).orElse(null));
            }
            return;
        }

        String raw = firstMeaningful(expense.getSubcategory(), expense.getCategory(),
                expense.getMerchantName(), originalText);
        if (raw == null) { expense.setCategory(null); expense.setSubcategory(null); return; }
        String alias = taxonomy.canonicalAlias(raw).or(() -> taxonomy.canonicalAliasInText(raw)).orElse(null);
        if (alias != null) {
            Optional<String> parent = taxonomy.parentCategory(alias);
            if (parent.isPresent()) { expense.setCategory(parent.get()); expense.setSubcategory(alias); }
            else { expense.setCategory(alias); expense.setSubcategory(null); }
            return;
        }
        Map<String, String> matches = semanticMatcher.match(taxonomy.allLabels().stream().sorted().toList(), List.of(raw));
        String resolved = taxonomy.canonicalLabel(matches == null ? null : matches.get(raw)).orElse(null);
        if (resolved == null) { expense.setCategory(null); expense.setSubcategory(null); return; }
        Optional<String> parent = taxonomy.parentCategory(resolved);
        if (parent.isPresent()) { expense.setCategory(parent.get()); expense.setSubcategory(resolved); }
        else { expense.setCategory(resolved); expense.setSubcategory(null); }
    }

    public Optional<String> resolveBudgetScope(String proposed, String originalText) {
        String explicit = explicitBudgetScope(originalText);
        String explicitCanonical = taxonomy.canonicalLabel(explicit)
                .or(() -> taxonomy.canonicalAlias(explicit))
                .or(() -> taxonomy.canonicalAliasInText(explicit)).orElse(null);
        if (explicitCanonical != null) return Optional.of(explicitCanonical);
        String exact = taxonomy.canonicalLabel(proposed).orElse(null);
        if (exact != null) return Optional.of(exact);
        String raw = firstMeaningful(proposed, originalText);
        if (raw == null) return Optional.empty();
        Map<String, String> matches = semanticMatcher.match(taxonomy.allLabels().stream().sorted().toList(), List.of(raw));
        return taxonomy.canonicalLabel(matches == null ? null : matches.get(raw));
    }

    private boolean isPhysicalPurchase(String text) {
        if (text == null || text.isBlank()) return false;
        return java.util.regex.Pattern.compile(
                "(?iu)\\b(?:bought|purchased|ordered|buying)\\b").matcher(text).find();
    }

    private String explicitBudgetScope(String text) {
        if (text == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)^(?:set|keep|setup|set\\s+up)\\s+(?:my\\s+)?(?:monthly\\s+)?(.+?)\\s+budget\\b")
                .matcher(text.trim());
        if (matcher.find()) return matcher.group(1).trim();
        matcher = java.util.regex.Pattern.compile(
                "(?i)^(?:my\\s+)?(.+?)\\s+(?:balance|budget|limit)\\s+for\\s+(?:this|the)\\s+month\\b")
                .matcher(text.trim());
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private Optional<String> resolveWithinCategory(String category, String raw) {
        if (raw == null) return Optional.empty();
        List<String> candidates = taxonomy.subcategoriesFor(category).stream().sorted().toList();
        if (candidates.isEmpty()) return Optional.empty();
        String alias = taxonomy.canonicalAlias(raw).or(() -> taxonomy.canonicalAliasInText(raw))
                .filter(candidates::contains).orElse(null);
        if (alias != null) return Optional.of(alias);
        Map<String, String> matches = semanticMatcher.match(candidates, List.of(raw));
        String resolved = matches == null ? null : matches.get(raw);
        return candidates.stream().filter(candidate -> candidate.equalsIgnoreCase(resolved)).findFirst();
    }

    private String firstMeaningful(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }
}
