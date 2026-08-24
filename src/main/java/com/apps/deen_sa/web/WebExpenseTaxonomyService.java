package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public Classification validate(String proposedCategory, String proposedSubcategory) {
        String category = taxonomy.canonicalLabel(proposedCategory)
                .filter(taxonomy::isCategory)
                .orElseThrow(() -> badRequest("Select a valid category"));
        String subcategory = taxonomy.canonicalLabel(proposedSubcategory)
                .filter(taxonomy.subcategoriesFor(category)::contains)
                .orElseThrow(() -> badRequest("Select a valid subcategory for " + category));
        return new Classification(category, subcategory);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record TaxonomyResponse(List<CategoryOption> categories) { }
    public record CategoryOption(String name, List<String> subcategories) { }
    public record Classification(String category, String subcategory) { }
}
