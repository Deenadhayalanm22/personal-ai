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
public class CategorySpendingGrowthRule implements MoneyStoryRule {
    private final MoneyStoriesProperties properties;
    public MoneyStoryType type() { return MoneyStoryType.CATEGORY_SPENDING_GROWTH; }
    public boolean enabled() { return properties.rules().categorySpendingGrowth().enabled(); }

    public Optional<MoneyStoryCandidate> evaluate(StoryEvaluationContext context) {
        var config = properties.rules().categorySpendingGrowth();
        return context.current().stream().collect(Collectors.groupingBy(CategoryDay::category)).entrySet().stream().map(entry -> {
            var previous = context.previousMonth().stream().filter(r -> r.category().equals(entry.getKey())).toList();
            BigDecimal current = sum(entry.getValue(), r -> true), prior = sum(previous, r -> true);
            BigDecimal delta = current.subtract(prior), percent = percent(delta, prior);
            boolean pattern = context.monthlyComparisonReady() && hasMonthlyCoverage(entry.getValue()) && hasMonthlyCoverage(previous)
                    && prior.signum() > 0 && current.compareTo(config.minCurrentAmount()) >= 0
                    && delta.compareTo(config.minAbsoluteGrowth()) >= 0 && percent.compareTo(config.minGrowthPercent()) >= 0;
            LocalDate end = pattern ? context.month().atEndOfMonth() : context.observationEnd();
            return new MoneyStoryCandidate(type(), pattern ? PATTERN : OBSERVATION, context.month().atDay(1), end,
                    pattern ? delta : current, count(entry.getValue()), pattern ? prior : ZERO, entry.getKey(),
                    pattern ? percent : null, null, null, context.month().atDay(1), end.plusDays(1));
        }).sorted(MoneyStorySelector.ORDER).findFirst();
    }
}
