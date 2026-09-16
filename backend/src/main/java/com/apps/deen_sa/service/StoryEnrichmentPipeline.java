package com.apps.deen_sa.service;

import org.springframework.stereotype.Service;
import java.util.List;

/** Composes independent optional lenses; there is no required or linear data-sharing ladder. */
@Service
public class StoryEnrichmentPipeline {
    private final List<StoryContextContributor> contributors;
    private final List<StoryInsightRule> rules;

    public StoryEnrichmentPipeline(List<StoryContextContributor> contributors, List<StoryInsightRule> rules) {
        this.contributors = contributors; this.rules = rules;
    }
    public StoryContext enrich(StoryEnrichmentRequest request) {
        StoryContext context = new StoryContext(request.coreFacts());
        contributors.stream().filter(item -> item.supports(request.storyType())).forEach(item -> item.contribute(request, context));
        rules.stream().filter(item -> item.supports(request.storyType())).map(item -> item.qualify(request, context))
                .flatMap(java.util.Optional::stream).forEach(context::addInsight);
        return context;
    }
}
