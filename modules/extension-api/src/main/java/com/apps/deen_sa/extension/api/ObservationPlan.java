package com.apps.deen_sa.extension.api;

import java.math.BigDecimal;
import java.time.Instant;

public record ObservationPlan(String subjectType, String subjectId, BigDecimal value, String unitId, Instant observedAt) { }
