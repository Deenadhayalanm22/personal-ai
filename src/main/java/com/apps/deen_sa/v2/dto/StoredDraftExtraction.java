package com.apps.deen_sa.v2.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StoredDraftExtraction(
        long extractionId,
        long draftId,
        String externalUserId,
        BigDecimal amount,
        String merchantName,
        String categoryId,
        String subcategoryId,
        LocalDate occurredAt,
        BigDecimal confidence
) {
}
