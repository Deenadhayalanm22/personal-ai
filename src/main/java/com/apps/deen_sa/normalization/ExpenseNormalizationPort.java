package com.apps.deen_sa.normalization;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExpenseNormalizationPort {
    ExpenseFacts normalize(String externalUserId, String rawText, LocalDate today);

    record ExpenseFacts(
            BigDecimal amount,
            String category,
            String subcategory,
            String merchant,
            String sourceAccount,
            LocalDate transactionDate,
            BigDecimal confidence
    ) {
    }
}
