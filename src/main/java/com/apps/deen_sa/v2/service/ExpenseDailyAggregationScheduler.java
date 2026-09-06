package com.apps.deen_sa.v2.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(name = "app.aggregation.scheduling-enabled", havingValue = "true")
@Slf4j
public class ExpenseDailyAggregationScheduler {
    private final ExpenseDailyAggregationService aggregationService;
    private final Clock clock;
    private final ZoneId aggregationZone;

    public ExpenseDailyAggregationScheduler(
            ExpenseDailyAggregationService aggregationService,
            Clock clock,
            @Value("${app.aggregation.time-zone:Asia/Kolkata}") String aggregationTimeZone
    ) {
        this.aggregationService = aggregationService;
        this.clock = clock;
        this.aggregationZone = ZoneId.of(aggregationTimeZone);
    }

    @Scheduled(cron = "${app.aggregation.daily-cron:0 0 1 * * *}",
            zone = "${app.aggregation.time-zone:Asia/Kolkata}")
    public void aggregatePreviousDay() {
        LocalDate date = LocalDate.now(clock.withZone(aggregationZone)).minusDays(1);
        int rows = aggregationService.rebuild(date);
        log.info("Rebuilt daily expense aggregates: date={}, aggregateRows={}", date, rows);
    }
}
