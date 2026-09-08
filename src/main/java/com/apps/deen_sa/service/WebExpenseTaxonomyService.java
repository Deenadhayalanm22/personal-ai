package com.apps.deen_sa.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebExpenseTaxonomyService {
    private final ExpenseTaxonomyRegistry taxonomy;

    public WebExpenseTaxonomyService(ExpenseTaxonomyRegistry taxonomy) {
        this.taxonomy = taxonomy;
    }

    public TaxonomyResponse options() {
        List<CategoryOption> categories = taxonomy.categories().stream().sorted()
                .map(category -> new CategoryOption(category,
                        taxonomy.subcategoriesFor(category).stream().sorted().toList()))
                .toList();
        return new TaxonomyResponse(categories);
    }

    public record TaxonomyResponse(List<CategoryOption> categories) { }
    public record CategoryOption(String name, List<String> subcategories) { }
}
