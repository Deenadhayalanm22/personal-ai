package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class ExpenseCalendarService {
    static final String INTENSITY_METHOD = "month-max-v1";
    private final StateChangeRepository expenses;

    public ExpenseCalendarService(StateChangeRepository expenses) {
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public CalendarResponse calendar(AppUserEntity user, YearMonth month) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Map<LocalDate, DailyAggregate> recorded = new HashMap<>();
        expenses.summarizeExpensesByLocalDay(String.valueOf(user.getId()), start, end, zone.getId())
                .forEach(row -> recorded.put(LocalDate.parse(String.valueOf(row[0])),
                        new DailyAggregate(((Number) row[1]).longValue(), amount(row[2]))));

        BigDecimal highest = recorded.values().stream().map(DailyAggregate::totalSpend)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<CalendarDay> days = month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
                .map(date -> {
                    DailyAggregate aggregate = recorded.getOrDefault(date, DailyAggregate.EMPTY);
                    return new CalendarDay(date, aggregate.transactionCount(), aggregate.totalSpend(),
                            intensity(aggregate.totalSpend(), highest));
                }).toList();
        long count = recorded.values().stream().mapToLong(DailyAggregate::transactionCount).sum();
        BigDecimal total = recorded.values().stream().map(DailyAggregate::totalSpend)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long recordedDays = recorded.values().stream().filter(value -> value.transactionCount() > 0).count();
        return new CalendarResponse(month.toString(), user.getCurrency(), zone.getId(), recordedDays, count,
                total, highest, INTENSITY_METHOD, days);
    }

    static int intensity(BigDecimal dailyTotal, BigDecimal highestSpend) {
        if (dailyTotal == null || dailyTotal.signum() == 0 || highestSpend == null || highestSpend.signum() == 0)
            return 0;
        BigDecimal ratio = dailyTotal.divide(highestSpend, 8, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.25")) <= 0) return 1;
        if (ratio.compareTo(new BigDecimal("0.50")) <= 0) return 2;
        if (ratio.compareTo(new BigDecimal("0.75")) <= 0) return 3;
        return 4;
    }

    private static BigDecimal amount(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private record DailyAggregate(long transactionCount, BigDecimal totalSpend) {
        private static final DailyAggregate EMPTY = new DailyAggregate(0, BigDecimal.ZERO);
    }

    public record CalendarResponse(String month, String currency, String timezone, long recordedDays,
                                   long transactionCount, BigDecimal totalSpend, BigDecimal highestSpend,
                                   String intensityMethod, List<CalendarDay> days) { }
    public record CalendarDay(LocalDate date, long transactionCount, BigDecimal totalSpend, int intensity) { }
}
