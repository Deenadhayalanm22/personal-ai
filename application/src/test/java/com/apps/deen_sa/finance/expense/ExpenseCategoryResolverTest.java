package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.llm.impl.TagSemanticMatcher;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCategoryResolverTest {
    private final ExpenseTaxonomyRegistry taxonomy = new ExpenseTaxonomyRegistry();

    @Test
    void acceptsOnlyModelSelectionsThatExistInConfiguredTaxonomy() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcher("weekly sabzi", "Groceries"));
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("weekly sabzi");

        resolver.canonicalize(expense, "Paid 900 for weekly sabzi");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isEqualTo("Groceries");
    }

    @Test
    void rejectsInventedModelLabelsInsteadOfPersistingFreeText() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcher("something unusual", "Made Up"));
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("something unusual");

        resolver.canonicalize(expense, "Paid 900 for something unusual");

        assertThat(expense.getCategory()).isNull();
        assertThat(expense.getSubcategory()).isNull();
    }

    private TagSemanticMatcher matcher(String raw, String resolved) {
        return new TagSemanticMatcher(null, null) {
            @Override public Map<String, String> match(List<String> canonical, List<String> values) {
                assertThat(canonical).contains("Groceries", "Fuel", "Medicines");
                return Map.of(raw, resolved);
            }
        };
    }
}
