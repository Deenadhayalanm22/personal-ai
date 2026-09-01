package com.apps.deen_sa.finance.expense;

import java.util.List;

/** Application-owned acceptance and progressive-enrichment assessment. */
public record ExpenseCompleteness(List<String> missingRequiredFields,
                                  List<String> missingEnrichmentFields) {
    public ExpenseCompleteness {
        missingRequiredFields = List.copyOf(missingRequiredFields);
        missingEnrichmentFields = List.copyOf(missingEnrichmentFields);
    }

    public boolean confirmable() { return missingRequiredFields.isEmpty(); }
}
