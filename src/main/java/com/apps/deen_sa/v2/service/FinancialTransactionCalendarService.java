package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinancialTransactionCalendarService {
    static final String INTENSITY_METHOD = "month-max-v1";

    private final FinancialTransactionRepository transactions;

    @Transactional(readOnly = true)
    public CalendarResponse calendar(AppUserEntity user, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        Map<LocalDate, DailyAggregate> recorded = new HashMap<>();
        transactions.summarizeByDay(user.getId(), start, end)
                .forEach(row -> recorded.put(
                        (LocalDate) row[0],
                        new DailyAggregate(
                                ((Number) row[1]).longValue(),
                                money((BigDecimal) row[2]))));

        BigDecimal highest = recorded.values().stream()
                .map(DailyAggregate::totalSpend)
                .max(BigDecimal::compareTo)
                .orElse(new BigDecimal("0.00"));
        List<CalendarDay> days = start.datesUntil(end)
                .map(date -> {
                    DailyAggregate aggregate = recorded.getOrDefault(
                            date, DailyAggregate.EMPTY);
                    return new CalendarDay(
                            date,
                            aggregate.transactionCount(),
                            aggregate.totalSpend(),
                            intensity(aggregate.totalSpend(), highest));
                })
                .toList();
        long count = recorded.values().stream()
                .mapToLong(DailyAggregate::transactionCount)
                .sum();
        BigDecimal total = recorded.values().stream()
                .map(DailyAggregate::totalSpend)
                .reduce(new BigDecimal("0.00"), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long recordedDays = recorded.values().stream()
                .filter(value -> value.transactionCount() > 0)
                .count();
        String timezone = ZoneId.of(user.getTimezone()).getId();

        return new CalendarResponse(
                month.toString(), user.getCurrency(), timezone, recordedDays,
                count, total, highest, INTENSITY_METHOD, days);
    }

    static int intensity(BigDecimal dailyTotal, BigDecimal highestSpend) {
        if (dailyTotal == null || dailyTotal.signum() == 0
                || highestSpend == null || highestSpend.signum() == 0) {
            return 0;
        }
        BigDecimal ratio = dailyTotal.divide(highestSpend, 8, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.25")) <= 0) {
            return 1;
        }
        if (ratio.compareTo(new BigDecimal("0.50")) <= 0) {
            return 2;
        }
        if (ratio.compareTo(new BigDecimal("0.75")) <= 0) {
            return 3;
        }
        return 4;
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private record DailyAggregate(long transactionCount, BigDecimal totalSpend) {
        private static final DailyAggregate EMPTY =
                new DailyAggregate(0, new BigDecimal("0.00"));
    }

    public record CalendarResponse(
            String month,
            String currency,
            String timezone,
            long recordedDays,
            long transactionCount,
            BigDecimal totalSpend,
            BigDecimal highestSpend,
            String intensityMethod,
            List<CalendarDay> days
    ) {
    }

    public record CalendarDay(
            LocalDate date,
            long transactionCount,
            BigDecimal totalSpend,
            int intensity
    ) {
    }
}
