package com.apps.deen_sa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executor;

/** FIN-EPIC-001 — Conversational expense capture. See docs/jira/personal-expense/FIN-EPIC-001-capture.md. */
@Component
@Slf4j
public class FirstWhatsAppMessageAggregationTrigger {
    private final ExpenseDailyAggregationService aggregation;
    private final DailyUserActionScheduler actions;
    private final Executor executor;
    private final Clock clock;
    private final ZoneId zone;
    private LocalDate completedDate;
    private LocalDate runningDate;

    public FirstWhatsAppMessageAggregationTrigger(
            ExpenseDailyAggregationService aggregation,
            DailyUserActionScheduler actions,
            @Qualifier("aggregationExecutor") Executor executor,
            Clock clock,
            @Value("${app.aggregation.time-zone:Asia/Kolkata}") String timeZone) {
        this.aggregation = aggregation;
        this.actions = actions;
        this.executor = executor;
        this.clock = clock;
        this.zone = ZoneId.of(timeZone);
    }

    public synchronized void triggerIfNeeded() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (today.equals(completedDate) || today.equals(runningDate)) return;
        runningDate = today;
        try {
            executor.execute(() -> run(today));
        } catch (RuntimeException failure) {
            runningDate = null;
            log.warn("Could not schedule daily aggregation for {}", today, failure);
        }
    }

    private void run(LocalDate today) {
        try {
            // Cover missed days after free-host downtime, and refresh yesterday even if it was
            // previously aggregated before a late transaction or correction arrived.
            aggregation.rebuildMissingBefore(today);
            aggregation.rebuild(today.minusDays(1));
            actions.evaluateActions();
            synchronized (this) {
                completedDate = today;
            }
            log.info("First-message aggregation completed for {}", today);
        } catch (RuntimeException failure) {
            log.error("First-message aggregation failed for {}; next message will retry", today, failure);
        } finally {
            synchronized (this) {
                if (today.equals(runningDate)) runningDate = null;
            }
        }
    }
}
