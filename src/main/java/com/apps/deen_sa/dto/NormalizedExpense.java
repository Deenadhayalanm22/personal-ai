package com.apps.deen_sa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NormalizedExpense(
        long draftId,
        String externalUserId,
        BigDecimal amount,
        String category,
        String subcategory,
        String merchant,
        String sourceAccount,
        LocalDate transactionDate,
        BigDecimal confidence
) {
}
