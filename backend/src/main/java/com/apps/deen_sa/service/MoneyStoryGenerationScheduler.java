package com.apps.deen_sa.service;

import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.MoneyStoryAggregateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;
import java.time.Clock;
import java.util.List;

/** The sole producer of Money Stories. Dashboard/API requests are read-only. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MoneyStoryGenerationScheduler {
    private final MoneyStoryAggregateRepository aggregates;
    private final AppUserRepository users;
    private final MoneyStoriesService stories;
    private final Clock clock;
    private final ExpenseDailyAggregationService dailyAggregation;
    @Value("${app.money-stories.batch-size:100}") private int batchSize;

    @Scheduled(cron = "${app.money-stories.generation-cron:0 */15 * * * *}",
            zone = "${app.aggregation.time-zone:Asia/Kolkata}")
    public void generateMissingAndStaleSnapshots() {
        dailyAggregation.rebuildChangedDates(batchSize);
        int generated = 0;
        List<MoneyStoryAggregateRepository.StoryMonth> storyGeneratedList = aggregates.monthsNeedingGeneration(batchSize, clock.instant());
        for (MoneyStoryAggregateRepository.StoryMonth target : storyGeneratedList) {
            users.findById(target.userId()).ifPresent(user -> {
                try { stories.generateIfNeeded(user, YearMonth.from(target.scopeMonth())); }
                catch (RuntimeException failure) { log.warn("Money story generation failed: userId={}, month={}",
                        target.userId(), target.scopeMonth(), failure); }
            });
            generated++;
        }
        if (generated > 0) log.info("Generated or refreshed {} Money Story snapshots", generated);
    }
}
