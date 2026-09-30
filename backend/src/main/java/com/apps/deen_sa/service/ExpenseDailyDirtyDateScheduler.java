package com.apps.deen_sa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rebuilds calendar dates marked dirty by expense creates, edits, and deletions. */
@Component
@RequiredArgsConstructor
public class ExpenseDailyDirtyDateScheduler {
    private final ExpenseDailyAggregationService aggregation;

    @Value("${app.aggregation.dirty-batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${app.aggregation.dirty-cron:0 */15 * * * *}",
            zone = "${app.aggregation.time-zone:Asia/Kolkata}")
    public void rebuildChangedDates() {
        aggregation.rebuildChangedDates(batchSize);
    }
}
