package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryLevel;
import org.springframework.stereotype.Component;
import java.util.*;

/** Stable product ordering shared by per-topic selection and the published deck. */
@Component
public class MoneyStorySelector {
    static final Comparator<MoneyStoryCandidate> ORDER = Comparator
            .comparingInt((MoneyStoryCandidate c) -> c.level() == MoneyStoryLevel.PATTERN ? 0 : 1)
            .thenComparing(MoneyStoryCandidate::periodStart, Comparator.reverseOrder())
            .thenComparing(c -> c.type().name())
            .thenComparing(c -> c.category() == null ? "" : c.category());

    public List<MoneyStoryCandidate> select(List<MoneyStoryCandidate> candidates) {
        return candidates.stream().sorted(ORDER).limit(3).toList();
    }
}
