package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryType;
import java.util.Optional;

/** Determines whether verified context is sufficient for one optional insight. */
public interface StoryInsightRule {
    boolean supports(MoneyStoryType storyType);
    Optional<StoryInsight> qualify(StoryEnrichmentRequest request, StoryContext context);
}
