package com.apps.deen_sa.service;

import com.apps.deen_sa.repository.MoneyStoryAggregateRepository.CategoryDay;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

final class MoneyStoryRuleSupport {
    static final BigDecimal ZERO = new BigDecimal("0.00");
    private MoneyStoryRuleSupport() { }
    static List<CategoryDay> in(List<CategoryDay> rows, LocalDate start, LocalDate end) {
        return rows.stream().filter(r -> !r.date().isBefore(start) && r.date().isBefore(end)).toList();
    }
    static BigDecimal sum(Collection<CategoryDay> rows, Predicate<CategoryDay> filter) {
        return rows.stream().filter(filter).map(CategoryDay::amount).reduce(ZERO, BigDecimal::add);
    }
    static int count(Collection<CategoryDay> rows) { return rows.stream().mapToInt(CategoryDay::count).sum(); }
    static long activeDays(Collection<CategoryDay> rows) { return rows.stream().map(CategoryDay::date).distinct().count(); }
    static long activeWeeks(Collection<CategoryDay> rows) {
        return rows.stream().map(r -> r.date().with(previousOrSame(DayOfWeek.MONDAY))).distinct().count();
    }
    static boolean hasMonthlyCoverage(List<CategoryDay> rows) { return activeDays(rows) >= 8 && activeWeeks(rows) >= 3; }
    static boolean everyCompletedWeek(List<CategoryDay> rows, LocalDate end, Predicate<List<CategoryDay>> qualifies) {
        for (int week = 1; week <= 4; week++) {
            if (!qualifies.test(in(rows, end.minusWeeks(week), end.minusWeeks(week - 1)))) return false;
        }
        return true;
    }
    static boolean weekend(LocalDate date) { return date.getDayOfWeek().getValue() >= 6; }
    static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() == 0 ? ZERO : numerator.multiply(new BigDecimal("100")).divide(denominator, 2, RoundingMode.HALF_UP);
    }
    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) return ZERO;
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2));
    }
}
