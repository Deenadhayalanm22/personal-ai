package com.apps.deen_sa.service;

import com.apps.deen_sa.repository.ExpenseDailyAggregateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseDailyAggregationService {
    private final ExpenseDailyAggregateRepository aggregates;

    /** Drain durable edit/backfill requests before stories read their projections. */
    @Transactional
    public void rebuildChangedDates(int limit) {
        for (java.sql.Date date : aggregates.lockDatesNeedingRebuild(limit)) {
            rebuild(date.toLocalDate());
            aggregates.clearRebuildRequest(date.toLocalDate());
        }
    }

    @Transactional
    public int rebuild(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Aggregate date is required");
        }
        aggregates.deleteForDate(date);
        aggregates.deleteReferenceForDate(date);
        return aggregates.aggregateForDate(date) + aggregates.aggregateReferencesForDate(date);
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
            aggregates.deleteReferenceForDate(date);
            aggregateRows += aggregates.aggregateForDate(date);
            aggregateRows += aggregates.aggregateReferencesForDate(date);
        }
        return new BackfillResult(List.copyOf(dates), aggregateRows);
    }

    public record BackfillResult(List<LocalDate> rebuiltDates, int aggregateRows) { }
}
