package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.conversation.ResponseMedia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
}
