package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
        assertThat(food.subcategories()).isSorted().contains("Groceries", "Eating Out");
    }

    @Test
    void canonicalizesAValidPairIgnoringCase() {
        var result = service.validate("food & dining", "groceries");
        assertThat(result.category()).isEqualTo("Food & Dining");
        assertThat(result.subcategory()).isEqualTo("Groceries");
    }

    @Test
    void rejectsSubcategoryFromAnotherCategory() {
        assertThatThrownBy(() -> service.validate("Transportation", "Groceries"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void rejectsInventedLabels() {
        assertThatThrownBy(() -> service.validate("Random", "Something"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }
}
