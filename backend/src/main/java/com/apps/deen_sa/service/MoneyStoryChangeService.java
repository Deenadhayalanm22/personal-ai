package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

/** Marks affected snapshots stale. Aggregate and story cron jobs own materialization work. */
@Service
@RequiredArgsConstructor
public class MoneyStoryChangeService {
    private final MoneyStoriesService stories;
    private final com.apps.deen_sa.repository.ExpenseDailyAggregateRepository aggregates;

    @Transactional
    public void changed(AppUserEntity user, LocalDate date) {
        aggregates.markDateForRebuild(date);
        stories.invalidate(user, date);
    }
}
