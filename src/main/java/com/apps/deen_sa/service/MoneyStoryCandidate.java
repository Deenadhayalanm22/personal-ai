package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryLevel;
import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.domain.SpendingNature;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Calculated facts only. A merchant label is a subject, never a category filter. */
public record MoneyStoryCandidate(MoneyStoryType type, MoneyStoryLevel level,
        LocalDate periodStart, LocalDate periodEnd, BigDecimal impact, int count,
        BigDecimal comparison, String category, BigDecimal percentOrRatio,
        SpendingNature nature, Long merchantId, LocalDate evidenceStart, LocalDate evidenceEnd) {
    public String logicalKey() {
        return type + ":" + periodStart + ":" + (merchantId != null ? merchantId : category == null ? "" : category);
    }
}
