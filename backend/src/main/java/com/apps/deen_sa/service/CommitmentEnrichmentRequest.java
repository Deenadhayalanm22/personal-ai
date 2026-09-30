package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentContextType;
import com.apps.deen_sa.entity.AppUserEntity;

import java.time.YearMonth;
import java.util.Map;

/** Verified core facts supplied by a story before optional domains enrich it. */
public record CommitmentEnrichmentRequest(AppUserEntity user, CommitmentContextType storyType, YearMonth month,
                                    Map<String, Object> coreFacts) { }
