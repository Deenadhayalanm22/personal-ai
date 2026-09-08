package com.apps.deen_sa.web;

import com.apps.deen_sa.service.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.domain.SpendingNature;
import com.apps.deen_sa.service.WebExpenseTaxonomyService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WebExpenseTaxonomyServiceTest {
    private final WebExpenseTaxonomyService service =
            new WebExpenseTaxonomyService(new ExpenseTaxonomyRegistry());

    @Test
    void returnsSortedCategoriesAndSubcategories() {
        var response = service.options();

        assertThat(response.categories()).extracting(WebExpenseTaxonomyService.CategoryOption::name)
                .isSorted().contains("Food & Dining", "Transportation");
        var food = response.categories().stream()
                .filter(category -> category.name().equals("Food & Dining")).findFirst().orElseThrow();
        assertThat(food.subcategories()).isSorted().contains("Groceries", "Restaurant & Cafe");
    }

    @Test
    void readsSpendingNatureFromTheSelectedTaxonomyPair() {
        ExpenseTaxonomyRegistry taxonomy = new ExpenseTaxonomyRegistry();

        assertThat(taxonomy.spendingNatureFor("Food & Dining", "Groceries"))
                .contains(SpendingNature.ESSENTIAL);
        assertThat(taxonomy.spendingNatureFor("Food & Dining", "Restaurant & Cafe"))
                .contains(SpendingNature.DISCRETIONARY);
        assertThat(taxonomy.spendingNatureFor("Transportation", "Auto & Taxi"))
                .contains(SpendingNature.FLEXIBLE);
    }

}
