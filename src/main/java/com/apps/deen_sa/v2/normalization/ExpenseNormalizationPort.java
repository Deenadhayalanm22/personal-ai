package com.apps.deen_sa.v2.normalization;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExpenseNormalizationPort {
    ExpenseFacts normalize(String externalUserId, String rawText, LocalDate today);

    record ExpenseFacts(
            BigDecimal amount,
            String category,
            String subcategory,
            String merchant,
            LocalDate transactionDate,
            BigDecimal confidence
    ) {
    }
}
