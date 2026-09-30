package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentContextType;
import java.util.Optional;

/** Determines whether verified context is sufficient for one optional insight. */
public interface CommitmentInsightRule {
    boolean supports(CommitmentContextType storyType);
    Optional<CommitmentInsight> qualify(CommitmentEnrichmentRequest request, CommitmentContext context);
}
