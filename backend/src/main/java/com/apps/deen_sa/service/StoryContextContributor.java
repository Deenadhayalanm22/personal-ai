package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryType;

/** Adds only facts owned and verified by one domain; it never renders story copy. */
public interface StoryContextContributor {
    boolean supports(MoneyStoryType storyType);
    void contribute(StoryEnrichmentRequest request, StoryContext context);
}
