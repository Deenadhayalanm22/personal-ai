package com.apps.deen_sa.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExpenseDailyAggregationSchedulerTest {

    @Test
    void aggregatesPreviousDayInIndiaAtScheduledExecution() {
        ExpenseDailyAggregationService service = mock(ExpenseDailyAggregationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-06T19:30:00Z"), ZoneOffset.UTC);
        ExpenseDailyAggregationScheduler scheduler =
                new ExpenseDailyAggregationScheduler(service, clock, "Asia/Kolkata");

        scheduler.aggregatePreviousDay();

        verify(service).rebuild(java.time.LocalDate.of(2026, 9, 6));
    }
}
