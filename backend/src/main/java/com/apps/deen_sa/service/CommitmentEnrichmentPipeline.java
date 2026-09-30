package com.apps.deen_sa.service;

import org.springframework.stereotype.Service;
import java.util.List;

/** Composes independent optional lenses; there is no required or linear data-sharing ladder. */
@Service
public class CommitmentEnrichmentPipeline {
    private final List<CommitmentContextContributor> contributors;
    private final List<CommitmentInsightRule> rules;

    public CommitmentEnrichmentPipeline(List<CommitmentContextContributor> contributors, List<CommitmentInsightRule> rules) {
        this.contributors = contributors; this.rules = rules;
    }
    public CommitmentContext enrich(CommitmentEnrichmentRequest request) {
        CommitmentContext context = new CommitmentContext(request.coreFacts());
        contributors.stream().filter(item -> item.supports(request.storyType())).forEach(item -> item.contribute(request, context));
        rules.stream().filter(item -> item.supports(request.storyType())).map(item -> item.qualify(request, context))
                .flatMap(java.util.Optional::stream).forEach(context::addInsight);
        return context;
    }
}
