package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.llm.impl.TagSemanticMatcher;
import org.springframework.stereotype.Component;
import java.util.*;

/** Resolves free text to configured taxonomy nodes; it never invents or hard-codes a category. */
@Component
public class ExpenseCategoryResolver {
    private final ExpenseTaxonomyRegistry taxonomy;
    private final TagSemanticMatcher semanticMatcher;
    public ExpenseCategoryResolver(ExpenseTaxonomyRegistry taxonomy, TagSemanticMatcher semanticMatcher) {
        this.taxonomy = taxonomy; this.semanticMatcher = semanticMatcher;
    }

    public void canonicalize(ExpenseDto expense, String originalText) {
        String subcategory = taxonomy.canonicalLabel(expense.getSubcategory()).orElse(null);
        String category = taxonomy.canonicalLabel(expense.getCategory()).orElse(null);
        if (subcategory != null && taxonomy.parentCategory(subcategory).isPresent()) {
            expense.setSubcategory(subcategory); expense.setCategory(taxonomy.parentCategory(subcategory).orElseThrow()); return;
        }
        if (category != null) {
            Optional<String> parent = taxonomy.parentCategory(category);
            if (parent.isPresent()) { expense.setCategory(parent.get()); expense.setSubcategory(category); }
            else { expense.setCategory(category); expense.setSubcategory(null); }
            return;
        }

        String raw = firstMeaningful(expense.getSubcategory(), expense.getCategory(), originalText);
        if (raw == null) { expense.setCategory(null); expense.setSubcategory(null); return; }
        Map<String, String> matches = semanticMatcher.match(taxonomy.allLabels().stream().sorted().toList(), List.of(raw));
        String resolved = taxonomy.canonicalLabel(matches == null ? null : matches.get(raw)).orElse(null);
        if (resolved == null) { expense.setCategory(null); expense.setSubcategory(null); return; }
        Optional<String> parent = taxonomy.parentCategory(resolved);
        if (parent.isPresent()) { expense.setCategory(parent.get()); expense.setSubcategory(resolved); }
        else { expense.setCategory(resolved); expense.setSubcategory(null); }
    }

    public Optional<String> resolveBudgetScope(String proposed, String originalText) {
        String exact = taxonomy.canonicalLabel(proposed).orElse(null);
        if (exact != null) return Optional.of(exact);
        String raw = firstMeaningful(proposed, originalText);
        if (raw == null) return Optional.empty();
        Map<String, String> matches = semanticMatcher.match(taxonomy.allLabels().stream().sorted().toList(), List.of(raw));
        return taxonomy.canonicalLabel(matches == null ? null : matches.get(raw));
    }

    private String firstMeaningful(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }
}
