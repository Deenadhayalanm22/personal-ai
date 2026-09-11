package com.apps.deen_sa.service;

import com.apps.deen_sa.config.MoneyStoriesProperties;
import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.repository.MoneyStoryAggregateRepository.CategoryDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.apps.deen_sa.service.MoneyStoryRuleSupport.*;
import static com.apps.deen_sa.domain.MoneyStoryLevel.*;

@Component
@RequiredArgsConstructor
public class UnusualHighSpendDayRule implements MoneyStoryRule {
    private final MoneyStoriesProperties properties;
    public MoneyStoryType type() { return MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY; }
    public boolean enabled() { return properties.rules().unusualHighSpendDay().enabled(); }

    public Optional<MoneyStoryCandidate> evaluate(StoryEvaluationContext context) {
        var config = properties.rules().unusualHighSpendDay();
        return context.current().stream().collect(Collectors.groupingBy(CategoryDay::date)).entrySet().stream().map(entry -> {
            LocalDate date = entry.getKey();
            var baseline = in(context.history(), date.minusWeeks(config.baselineWeeks()), date);
            var totals = baseline.stream().collect(Collectors.groupingBy(CategoryDay::date)).values().stream()
                    .map(rows -> sum(rows, r -> true)).filter(n -> n.signum() > 0).toList();
            BigDecimal median = median(totals), amount = sum(entry.getValue(), r -> true);
            BigDecimal ratio = median.signum() == 0 ? ZERO : amount.divide(median, 2, RoundingMode.HALF_UP);
            boolean pattern = totals.size() >= config.minBaselineActiveDays() && activeWeeks(baseline) >= 4
                    && median.signum() > 0 && amount.compareTo(config.minDayAmount()) >= 0
                    && ratio.compareTo(config.minMultipleOfMedian()) >= 0;
            return new MoneyStoryCandidate(type(), pattern ? PATTERN : OBSERVATION, date, date, amount,
                    count(entry.getValue()), pattern ? median : ZERO, null, pattern ? ratio : null,
                    null, null, date, date.plusDays(1));
        }).sorted(MoneyStorySelector.ORDER).findFirst();
    }
}
