package com.apps.deen_sa.dto;

import java.math.BigDecimal;

public record RecordedExpense(
        String externalUserId,
        BigDecimal amount,
        String merchantName
) {
}
