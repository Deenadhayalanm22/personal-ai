package com.apps.deen_sa.v2.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NormalizedExpense(
        long draftId,
        String externalUserId,
        BigDecimal amount,
        String category,
        String subcategory,
        String merchant,
        LocalDate transactionDate,
        BigDecimal confidence
) {
}
