package com.apps.deen_sa.service;

import com.apps.deen_sa.config.MoneyStoriesProperties;
import com.apps.deen_sa.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.apps.deen_sa.service.MoneyStoryRuleSupport.*;
import static com.apps.deen_sa.domain.MoneyStoryLevel.*;

@Component
@RequiredArgsConstructor
public class DiscretionaryFrequencyRule implements MoneyStoryRule {
    private final MoneyStoriesProperties properties;
    public MoneyStoryType type() { return MoneyStoryType.DISCRETIONARY_FREQUENCY; }
    public boolean enabled() { return properties.rules().discretionaryFrequency().enabled(); }

    public Optional<MoneyStoryCandidate> evaluate(StoryEvaluationContext context) {
        var weekly = context.fourCompletedWeeks().stream().filter(r -> "DISCRETIONARY".equals(r.nature())).toList();
        boolean pattern = everyCompletedWeek(weekly, context.completedWeeksEnd(), rows ->
                count(rows) >= properties.rules().discretionaryFrequency().minTransactionCount() && activeDays(rows) >= 2);
        var rows = pattern ? weekly : context.current().stream().filter(r -> "DISCRETIONARY".equals(r.nature())).toList();
        if (rows.isEmpty()) return Optional.empty();
        LocalDate start = pattern ? context.completedWeeksEnd().minusWeeks(4) : context.month().atDay(1);
        LocalDate end = pattern ? context.completedWeeksEnd() : context.observationEnd().plusDays(1);
        return Optional.of(new MoneyStoryCandidate(type(), pattern ? PATTERN : OBSERVATION, start, end.minusDays(1),
                sum(rows, r -> true), count(rows), ZERO, null, null, SpendingNature.DISCRETIONARY, null, start, end));
    }
}
