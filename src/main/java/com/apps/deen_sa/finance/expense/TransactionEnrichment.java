package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;

public record TransactionEnrichment(ExpenseDto proposal, EnrichmentSource source) { }
