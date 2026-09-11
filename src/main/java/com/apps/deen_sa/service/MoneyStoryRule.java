package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryType;

import java.util.Optional;

/**
 * One independently configurable story rule. Spring discovers implementations automatically.
 */
public interface MoneyStoryRule {
    MoneyStoryType type();

    boolean enabled();

    Optional<MoneyStoryCandidate> evaluate(StoryEvaluationContext context);
}
