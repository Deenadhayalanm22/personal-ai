package com.apps.deen_sa.service;

import com.apps.deen_sa.config.MoneyStoriesProperties;
import com.apps.deen_sa.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.apps.deen_sa.service.MoneyStoryRuleSupport.*;
import static com.apps.deen_sa.domain.MoneyStoryLevel.*;

@Component
@RequiredArgsConstructor
public class WeekendSpendingPatternRule implements MoneyStoryRule {
    private final MoneyStoriesProperties properties;
    public MoneyStoryType type() { return MoneyStoryType.WEEKEND_SPENDING_PATTERN; }
    public boolean enabled() { return properties.rules().weekendSpendingPattern().enabled(); }

    public Optional<MoneyStoryCandidate> evaluate(StoryEvaluationContext context) {
        var config = properties.rules().weekendSpendingPattern();
        var weekly = context.fourCompletedWeeks();
        BigDecimal weeklyTotal = sum(weekly, r -> true), weekendTotal = sum(weekly, r -> weekend(r.date()));
        boolean pattern = everyCompletedWeek(weekly, context.completedWeeksEnd(), rows ->
                activeDays(rows.stream().filter(r -> weekend(r.date())).toList()) >= 1
                && activeDays(rows.stream().filter(r -> !weekend(r.date())).toList()) >= 2)
                && weekendTotal.compareTo(config.minWeekendAmount()) >= 0
                && percent(weekendTotal, weeklyTotal).compareTo(config.minWeekendSharePercent()) >= 0;
        var all = pattern ? weekly : context.current();
        var rows = all.stream().filter(r -> weekend(r.date())).toList();
        if (rows.isEmpty()) return Optional.empty();
        LocalDate start = pattern ? context.completedWeeksEnd().minusWeeks(4) : context.month().atDay(1);
        LocalDate end = pattern ? context.completedWeeksEnd() : context.observationEnd().plusDays(1);
        BigDecimal amount = sum(rows, r -> true), total = sum(all, r -> true);
        return Optional.of(new MoneyStoryCandidate(type(), pattern ? PATTERN : OBSERVATION, start, end.minusDays(1),
                amount, count(rows), total, null, pattern ? percent(amount, total) : null, null, null, start, end));
    }
}
