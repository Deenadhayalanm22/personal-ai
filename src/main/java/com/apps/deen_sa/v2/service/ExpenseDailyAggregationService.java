package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.v2.repository.ExpenseDailyAggregateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseDailyAggregationService {
    private final ExpenseDailyAggregateRepository aggregates;

    @Transactional
    public int rebuild(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Aggregate date is required");
        }
        aggregates.deleteForDate(date);
        return aggregates.aggregateForDate(date);
    }

    @Transactional
    public BackfillResult rebuildMissingBefore(LocalDate exclusiveEnd) {
        if (exclusiveEnd == null) {
            throw new IllegalArgumentException("Backfill end date is required");
        }
        List<LocalDate> dates = aggregates.findMissingDatesBefore(exclusiveEnd).stream()
                .map(java.sql.Date::toLocalDate)
                .toList();
        int aggregateRows = 0;
        for (LocalDate date : dates) {
            aggregates.deleteForDate(date);
            aggregateRows += aggregates.aggregateForDate(date);
        }
        return new BackfillResult(List.copyOf(dates), aggregateRows);
    }

    public record BackfillResult(List<LocalDate> rebuiltDates, int aggregateRows) { }
}
