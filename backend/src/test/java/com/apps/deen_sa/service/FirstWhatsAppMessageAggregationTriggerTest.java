package com.apps.deen_sa.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;

class FirstWhatsAppMessageAggregationTriggerTest {
    @Test
    void queuesOncePerLocalDayAndBackfillsMissedDatesBeforeRefreshingYesterday() {
        ExpenseDailyAggregationService aggregation = mock(ExpenseDailyAggregationService.class);
        DailyUserActionScheduler actions = mock(DailyUserActionScheduler.class);
        Queue<Runnable> queued = new ArrayDeque<>();
        Executor executor = queued::add;
        Clock clock = Clock.fixed(Instant.parse("2026-09-06T19:30:00Z"), ZoneOffset.UTC);
        var trigger = new FirstWhatsAppMessageAggregationTrigger(
                aggregation, actions, executor, clock, "Asia/Kolkata");

        trigger.triggerIfNeeded();
        trigger.triggerIfNeeded();
        org.junit.jupiter.api.Assertions.assertEquals(1, queued.size());
        queued.remove().run();
        trigger.triggerIfNeeded();
        org.junit.jupiter.api.Assertions.assertTrue(queued.isEmpty());

        var order = inOrder(aggregation, actions);
        order.verify(aggregation).rebuildMissingBefore(LocalDate.of(2026, 9, 7));
        order.verify(aggregation).rebuild(LocalDate.of(2026, 9, 6));
        order.verify(actions).evaluateActions();
    }

    @Test
    void failedBackgroundWorkCanRetryOnNextMessage() {
        ExpenseDailyAggregationService aggregation = mock(ExpenseDailyAggregationService.class);
        DailyUserActionScheduler actions = mock(DailyUserActionScheduler.class);
        Queue<Runnable> queued = new ArrayDeque<>();
        Clock clock = Clock.fixed(Instant.parse("2026-09-06T19:30:00Z"), ZoneOffset.UTC);
        var trigger = new FirstWhatsAppMessageAggregationTrigger(
                aggregation, actions, queued::add, clock, "Asia/Kolkata");
        doThrow(new IllegalStateException("database unavailable")).doReturn(1)
                .when(aggregation).rebuild(LocalDate.of(2026, 9, 6));

        trigger.triggerIfNeeded();
        queued.remove().run();
        trigger.triggerIfNeeded();
        org.junit.jupiter.api.Assertions.assertEquals(1, queued.size());
        queued.remove().run();

        verify(aggregation, times(2)).rebuild(LocalDate.of(2026, 9, 6));
        verify(actions).evaluateActions();
    }
}
