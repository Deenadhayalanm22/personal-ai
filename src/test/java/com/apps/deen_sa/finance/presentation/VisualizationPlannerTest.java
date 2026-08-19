package com.apps.deen_sa.finance.presentation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizationPlannerTest {
    private final VisualizationPlanner planner = new VisualizationPlanner();

    @Test
    void mapsSupportedQuestionsToStableVisualizations() {
        assertType("SPENDING_OVERVIEW", VisualizationType.SPENDING_REPORT_CARD);
        assertType("CATEGORY_RANKING", VisualizationType.RANKED_HORIZONTAL_BARS);
        assertType("DAILY_PATTERN", VisualizationType.CALENDAR_HEATMAP);
        assertType("PERIOD_COMPARISON", VisualizationType.PAIRED_BARS);
        assertType("MONEY_FLOW", VisualizationType.SANKEY_MONEY_FLOW);
        assertType("BUDGET_PROGRESS", VisualizationType.BUDGET_PROGRESS_BARS);
        assertType("CATEGORY_HIERARCHY", VisualizationType.CATEGORY_TREEMAP);
    }

    @Test
    void moodDoesNotChangeTheDataContractOrGraphicType() {
        VisualizationPlan neutral = plan("SPENDING_OVERVIEW", "NEUTRAL");
        VisualizationPlan concerned = plan("SPENDING_OVERVIEW", "CONCERNED");

        assertThat(concerned.type()).isEqualTo(neutral.type());
        assertThat(concerned.metrics()).isEqualTo(neutral.metrics());
        assertThat(concerned.dimensions()).isEqualTo(neutral.dimensions());
        assertThat(concerned.mood()).isEqualTo(PresentationMood.CONCERNED);
    }

    private void assertType(String intent, VisualizationType expected) {
        assertThat(plan(intent, "NEUTRAL").type()).isEqualTo(expected);
    }

    private VisualizationPlan plan(String intent, String mood) {
        return planner.plan(FinancialPresentationRequest.fromAi(intent, mood));
    }
}
