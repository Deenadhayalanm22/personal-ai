package com.apps.deen_sa.finance.presentation;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VisualizationPlanner {
    public VisualizationPlan plan(FinancialPresentationRequest request) {
        return switch (request.intent()) {
            case SPENDING_OVERVIEW -> plan(request, VisualizationType.SPENDING_REPORT_CARD,
                    Set.of(Metric.TOTAL_SPEND), Set.of(Dimension.CATEGORY, Dimension.SUBCATEGORY), false);
            case CATEGORY_RANKING -> plan(request, VisualizationType.RANKED_HORIZONTAL_BARS,
                    Set.of(Metric.TOTAL_SPEND), Set.of(Dimension.CATEGORY), false);
            case DAILY_PATTERN -> plan(request, VisualizationType.CALENDAR_HEATMAP,
                    Set.of(Metric.TOTAL_SPEND), Set.of(Dimension.DAY), false);
            case PERIOD_COMPARISON -> plan(request, VisualizationType.PAIRED_BARS,
                    Set.of(Metric.TOTAL_SPEND), Set.of(Dimension.CATEGORY, Dimension.PERIOD), true);
            case MONEY_FLOW -> plan(request, VisualizationType.SANKEY_MONEY_FLOW,
                    Set.of(Metric.INCOME_AMOUNT, Metric.TOTAL_SPEND),
                    Set.of(Dimension.ACCOUNT, Dimension.MOVEMENT_TYPE, Dimension.CATEGORY), false);
            case ACCOUNT_OVERVIEW -> plan(request, VisualizationType.ACCOUNT_STACK,
                    Set.of(Metric.ACCOUNT_BALANCE), Set.of(Dimension.ACCOUNT), false);
            case BUDGET_PROGRESS -> plan(request, VisualizationType.BUDGET_PROGRESS_BARS,
                    Set.of(Metric.BUDGET_AMOUNT, Metric.TOTAL_SPEND), Set.of(Dimension.CATEGORY), false);
            case CATEGORY_HIERARCHY -> plan(request, VisualizationType.CATEGORY_TREEMAP,
                    Set.of(Metric.TOTAL_SPEND), Set.of(Dimension.CATEGORY, Dimension.SUBCATEGORY, Dimension.MERCHANT), false);
        };
    }

    private VisualizationPlan plan(FinancialPresentationRequest request, VisualizationType type,
                                   Set<Metric> metrics, Set<Dimension> dimensions, boolean comparison) {
        return new VisualizationPlan(type, request.intent(), request.mood(), metrics, dimensions, comparison);
    }
}
