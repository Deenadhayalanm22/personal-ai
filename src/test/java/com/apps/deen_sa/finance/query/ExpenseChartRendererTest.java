package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.conversation.ResponseMedia;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.finance.presentation.PresentationMood;
import com.apps.deen_sa.finance.budget.BudgetProgress;
import com.apps.deen_sa.finance.presentation.FlowPoint;
import com.apps.deen_sa.finance.presentation.HierarchyPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseChartRendererTest {
    private final ExpenseChartRenderer renderer = new ExpenseChartRenderer();

    @Test
    void rendersCategoryValuesAsAPng() {
        ResponseMedia media = renderer.categoryDonut("This month's spending",
                Map.of("Food", new BigDecimal("12000.00"), "Travel", new BigDecimal("3500")), "en-IN");

        assertThat(media.contentType()).isEqualTo("image/png");
        assertThat(media.filename()).isEqualTo("expense-breakdown.png");
        assertThat(media.content()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(media.content().length).isGreaterThan(10_000);
    }

    @Test
    void keepsSevenLargestCategoriesAndAggregatesTheRest() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (int amount = 10; amount >= 1; amount--) values.put("Category " + amount, BigDecimal.valueOf(amount));

        Map<String, BigDecimal> displayed = renderer.displayValues(values);

        assertThat(displayed).hasSize(8);
        assertThat(displayed.get("Other")).isEqualByComparingTo("6");
        assertThat(displayed.keySet()).contains("Category 10", "Category 4").doesNotContain("Category 3");
    }

    @Test
    void skipsEmptyAndNonPositiveValues() {
        assertThat(renderer.categoryDonut("Empty", Map.of("Food", BigDecimal.ZERO), "en-IN")).isNull();
    }

    @Test
    void rendersMobileSpendingReportCard() {
        ExpenseSummary summary = new ExpenseSummary();
        summary.setTotalSpend(new BigDecimal("7445"));
        summary.setSpendByCategory(Map.of("Food & Dining", new BigDecimal("3439"),
                "Family Support", new BigDecimal("2500"), "Education", new BigDecimal("760")));

        ResponseMedia media = renderer.reportCard("August spending", summary, PresentationMood.CONCERNED, "en-IN");

        assertThat(media.filename()).isEqualTo("spending-report-card.png");
        assertThat(media.content()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(media.content().length).isGreaterThan(10_000);
    }

    @Test
    void rendersAccountStackAsItsOwnGraphic() {
        ResponseMedia media = renderer.accountStack("Your accounts",
                Map.of("HDFC", new BigDecimal("128400"), "Cash", new BigDecimal("3200")),
                PresentationMood.NEUTRAL, "en-IN");

        assertThat(media.filename()).isEqualTo("account-stack.png");
        assertThat(media.content()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(media.content().length).isGreaterThan(10_000);
    }

    @Test
    void rendersBudgetProgressAndEmptyState() {
        ResponseMedia progress = renderer.budgetProgress("Budgets", List.of(
                new BudgetProgress("Groceries", new BigDecimal("8500"), new BigDecimal("10000"))),
                PresentationMood.CONCERNED, "en-IN");
        ResponseMedia empty = renderer.budgetProgress("Budgets", List.of(), PresentationMood.NEUTRAL, "en-IN");

        assertThat(progress.filename()).isEqualTo("budget-progress.png");
        assertThat(progress.content().length).isGreaterThan(10_000);
        assertThat(empty.content()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
    }

    @Test
    void specializedAnalyticsProduceDistinctGraphics() {
        ResponseMedia heatmap = renderer.calendarHeatmap("Daily", Map.of(
                "2026-08-18", new BigDecimal("200"), "2026-08-19", new BigDecimal("900")),
                PresentationMood.NEUTRAL, "en-IN");
        ResponseMedia comparison = renderer.comparisonBars("Comparison",
                Map.of("Food", new BigDecimal("900")), Map.of("Food", new BigDecimal("600")),
                PresentationMood.CURIOUS, "en-IN");
        ResponseMedia flow = renderer.moneyFlow("Flow", List.of(
                new FlowPoint("HDFC", "Food", new BigDecimal("900"))), new BigDecimal("5000"),
                PresentationMood.NEUTRAL, "en-IN");
        ResponseMedia treemap = renderer.categoryTreemap("Hierarchy", List.of(
                new HierarchyPoint("Food", "Groceries", "Amazon", new BigDecimal("900"))),
                PresentationMood.NEUTRAL, "en-IN");

        assertThat(List.of(heatmap.filename(), comparison.filename(), flow.filename(), treemap.filename()))
                .containsExactly("daily-heatmap.png", "period-comparison.png", "money-flow.png", "category-treemap.png")
                .doesNotHaveDuplicates();
        assertThat(List.of(heatmap, comparison, flow, treemap)).allSatisfy(media ->
                assertThat(media.content()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47));
    }
}
