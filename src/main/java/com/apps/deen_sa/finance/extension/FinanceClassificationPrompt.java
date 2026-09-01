package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;

final class FinanceClassificationPrompt implements InterpretationPromptContributor {
    private final ExpenseTaxonomyRegistry taxonomy;

    FinanceClassificationPrompt(ExpenseTaxonomyRegistry taxonomy) {
        this.taxonomy = taxonomy;
    }

    @Override public String instructions() { return """
            PERSONAL-FINANCE CLASSIFICATION
            Classification fields: category, subcategory, taxonomyCandidate.
            - Use only the configured broad category and one of its configured subcategories. Never invent classification.
            - Food & Dining restaurant/cafe/bar/meals and prepared-meal delivery are Eating Out. Snacks & Beverages is
              for snacks or drinks that are not meals. A restaurant family dinner remains Eating Out.
            - School bags, books, stationery, and classroom materials are School Supplies, not School Fees.
            - Ordinary ingredients bought for cooking are Groceries. Celebration Meal/Home Cooked requires explicit
              evidence of a celebration or special meal prepared at home.
            - Physical electronics are Electronics; clothing, footwear and wearable accessories are Clothing; Gifts
              requires explicit gift evidence; otherwise identifiable physical goods use Shopping / Other Shopping.
            - Travel is out-of-town: lodging is Accommodation, airfare is Flights, long-distance train/bus is Intercity
              Transport, sightseeing is Travel Activities, and visa/passport charges are Travel Documents & Fees.
              Everyday fuel, maintenance, parking, tolls, autos, and local commuting remain Transportation.
            - Miscellaneous is a last resort, not a fallback for an unfamiliar product name.
            - taxonomyCandidate is only a reusable review suggestion when no configured specific subcategory fits.
              It never replaces configured classification and must not name merchants, brands, people or locations.

            CONFIGURED TAXONOMY (authoritative):
            """ + renderTaxonomy(); }

    private String renderTaxonomy() {
        StringBuilder configured = new StringBuilder();
        taxonomy.categories().forEach(category -> {
            configured.append("- ").append(category).append(":\n");
            taxonomy.subcategoriesFor(category)
                    .forEach(subcategory -> configured.append("  - ").append(subcategory).append("\n"));
        });
        return configured.toString();
    }
}
