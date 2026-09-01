package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceClassificationPromptTest {
    @Test
    void rendersAllowedCategoriesAndSubcategoriesFromConfiguredTaxonomy() {
        String instructions = new FinanceClassificationPrompt(new ExpenseTaxonomyRegistry()).instructions();

        assertThat(instructions).contains("CONFIGURED TAXONOMY (authoritative):")
                .contains("- Food & Dining:")
                .contains("  - Groceries")
                .contains("- Travel:")
                .contains("  - Accommodation");
    }
}
