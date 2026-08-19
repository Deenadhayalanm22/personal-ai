package com.apps.deen_sa.finance.presentation;

import java.util.Set;

public record VisualizationPlan(
        VisualizationType type,
        AnalysisIntent intent,
        PresentationMood mood,
        Set<Metric> metrics,
        Set<Dimension> dimensions,
        boolean comparisonRequired
) {
    public VisualizationPlan {
        metrics = Set.copyOf(metrics);
        dimensions = Set.copyOf(dimensions);
    }
}
