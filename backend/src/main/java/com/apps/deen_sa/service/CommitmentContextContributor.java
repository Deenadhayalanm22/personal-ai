package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentContextType;

/** Adds only facts owned and verified by one domain; it never renders story copy. */
public interface CommitmentContextContributor {
    boolean supports(CommitmentContextType storyType);
    void contribute(CommitmentEnrichmentRequest request, CommitmentContext context);
}
