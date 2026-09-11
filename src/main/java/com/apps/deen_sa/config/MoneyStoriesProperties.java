package com.apps.deen_sa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

/** Product-owned eligibility settings. Keep monetary values currency-specific until multi-currency policy exists. */
@ConfigurationProperties(prefix = "app.money-stories")
public record MoneyStoriesProperties(Rules rules) {
    public record Rules(
            DiscretionaryFrequency discretionaryFrequency,
            CategorySpendingGrowth categorySpendingGrowth,
            WeekendSpendingPattern weekendSpendingPattern,
            MerchantConcentration merchantConcentration,
            UnusualHighSpendDay unusualHighSpendDay) { }
    public record DiscretionaryFrequency(boolean enabled, int minTransactionCount) { }
    public record CategorySpendingGrowth(boolean enabled, BigDecimal minCurrentAmount, BigDecimal minAbsoluteGrowth,
                                         BigDecimal minGrowthPercent) { }
    public record WeekendSpendingPattern(boolean enabled, BigDecimal minWeekendAmount, BigDecimal minWeekendSharePercent) { }
    public record MerchantConcentration(boolean enabled, BigDecimal minMerchantAmount, int minTransactionCount,
                                        BigDecimal minMonthlySharePercent) { }
    public record UnusualHighSpendDay(boolean enabled, BigDecimal minDayAmount, BigDecimal minMultipleOfMedian,
                                      int baselineWeeks, int minBaselineActiveDays) { }
}
