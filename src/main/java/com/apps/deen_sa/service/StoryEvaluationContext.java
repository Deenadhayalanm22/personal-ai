package com.apps.deen_sa.service;

import com.apps.deen_sa.repository.MoneyStoryAggregateRepository.CategoryDay;
import com.apps.deen_sa.repository.MoneyStoryAggregateRepository.ReferenceDay;
import java.time.*;
import java.util.List;
import java.util.stream.Stream;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

public record StoryEvaluationContext(YearMonth month, LocalDate today, List<CategoryDay> current,
        List<CategoryDay> previousMonth, List<CategoryDay> baseline, List<ReferenceDay> references) {
    public List<CategoryDay> history() { return Stream.concat(baseline.stream(), current.stream()).toList(); }
    public LocalDate completedWeeksEnd() {
        LocalDate boundary = today.isBefore(month.plusMonths(1).atDay(1)) ? today : month.plusMonths(1).atDay(1);
        return boundary.with(previousOrSame(DayOfWeek.MONDAY));
    }
    public List<CategoryDay> fourCompletedWeeks() {
        return MoneyStoryRuleSupport.in(history(), completedWeeksEnd().minusWeeks(4), completedWeeksEnd());
    }
    public LocalDate observationEnd() {
        return current.stream().map(CategoryDay::date).max(LocalDate::compareTo).orElse(month.atDay(1));
    }
    public boolean monthlyComparisonReady() { return !today.isBefore(month.plusMonths(1).atDay(3)); }
}
