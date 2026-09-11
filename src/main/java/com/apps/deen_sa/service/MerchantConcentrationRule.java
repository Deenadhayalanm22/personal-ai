package com.apps.deen_sa.service;

import com.apps.deen_sa.config.MoneyStoriesProperties;
import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.repository.MoneyStoryAggregateRepository.ReferenceDay;
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
public class MerchantConcentrationRule implements MoneyStoryRule {
    private final MoneyStoriesProperties properties;
    public MoneyStoryType type() { return MoneyStoryType.MERCHANT_CONCENTRATION; }
    public boolean enabled() { return properties.rules().merchantConcentration().enabled(); }

    public Optional<MoneyStoryCandidate> evaluate(StoryEvaluationContext context) {
        var config = properties.rules().merchantConcentration();
        LocalDate weekEnd = context.completedWeeksEnd(), weekStart = weekEnd.minusWeeks(4);
        BigDecimal total = sum(context.fourCompletedWeeks(), r -> true);
        return context.references().stream().collect(Collectors.groupingBy(ReferenceDay::referenceId)).values().stream().map(group -> {
            var weekly = group.stream().filter(r -> !r.date().isBefore(weekStart) && r.date().isBefore(weekEnd)).toList();
            boolean coverage = true;
            for (int week = 1; week <= 4; week++) {
                LocalDate from = weekEnd.minusWeeks(week), to = from.plusWeeks(1);
                var rows = weekly.stream().filter(r -> !r.date().isBefore(from) && r.date().isBefore(to)).toList();
                coverage &= rows.stream().mapToInt(ReferenceDay::count).sum() >= config.minTransactionCount()
                        && rows.stream().map(ReferenceDay::date).distinct().count() >= 2;
            }
            BigDecimal weeklyAmount = weekly.stream().map(ReferenceDay::amount).reduce(ZERO, BigDecimal::add);
            boolean pattern = coverage && weeklyAmount.compareTo(config.minMerchantAmount()) >= 0
                    && percent(weeklyAmount, total).compareTo(config.minMonthlySharePercent()) >= 0;
            var rows = pattern ? weekly : group.stream().filter(r -> !r.date().isBefore(context.month().atDay(1))).toList();
            if (rows.isEmpty()) return null;
            LocalDate start = pattern ? weekStart : context.month().atDay(1);
            LocalDate end = pattern ? weekEnd : context.observationEnd().plusDays(1);
            BigDecimal amount = rows.stream().map(ReferenceDay::amount).reduce(ZERO, BigDecimal::add);
            return new MoneyStoryCandidate(type(), pattern ? PATTERN : OBSERVATION, start, end.minusDays(1), amount,
                    rows.stream().mapToInt(ReferenceDay::count).sum(), pattern ? total : ZERO, rows.getFirst().referenceName(),
                    pattern ? percent(amount, total) : null, null, rows.getFirst().referenceId(), start, end);
        }).filter(Objects::nonNull).sorted(MoneyStorySelector.ORDER).findFirst();
    }
}
