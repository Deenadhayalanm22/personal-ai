package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.v2.repository.ExpenseDailyAggregateRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpenseDailyAggregationServiceTest {

    @Test
    void rebuildsTheDateAndReturnsCreatedAggregateCount() {
        ExpenseDailyAggregateRepository repository = mock(ExpenseDailyAggregateRepository.class);
        ExpenseDailyAggregationService service = new ExpenseDailyAggregationService(repository);
        LocalDate date = LocalDate.of(2026, 9, 5);
        when(repository.aggregateForDate(date)).thenReturn(4);

        int rows = service.rebuild(date);

        assertThat(rows).isEqualTo(4);
        var ordered = inOrder(repository);
        ordered.verify(repository).deleteForDate(date);
        ordered.verify(repository).aggregateForDate(date);
    }

    @Test
    void rebuildsEveryMissingDateInChronologicalOrder() {
        ExpenseDailyAggregateRepository repository = mock(ExpenseDailyAggregateRepository.class);
        ExpenseDailyAggregationService service = new ExpenseDailyAggregationService(repository);
        LocalDate first = LocalDate.of(2026, 9, 3);
        LocalDate second = LocalDate.of(2026, 9, 5);
        when(repository.findMissingDatesBefore(LocalDate.of(2026, 9, 6)))
                .thenReturn(List.of(java.sql.Date.valueOf(first), java.sql.Date.valueOf(second)));
        when(repository.aggregateForDate(first)).thenReturn(2);
        when(repository.aggregateForDate(second)).thenReturn(3);

        var result = service.rebuildMissingBefore(LocalDate.of(2026, 9, 6));

        assertThat(result.rebuiltDates()).containsExactly(first, second);
        assertThat(result.aggregateRows()).isEqualTo(5);
        var ordered = inOrder(repository);
        ordered.verify(repository).deleteForDate(first);
        ordered.verify(repository).aggregateForDate(first);
        ordered.verify(repository).deleteForDate(second);
        ordered.verify(repository).aggregateForDate(second);
    }
}
