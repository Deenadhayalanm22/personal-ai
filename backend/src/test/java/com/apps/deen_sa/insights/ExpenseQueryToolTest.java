package com.apps.deen_sa.insights;

import com.apps.deen_sa.exception.WebApiException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ExpenseQueryToolTest {
    private ExpenseQueryTool.Query query(List<String> groups, List<ExpenseQueryTool.Filter> filters) {
        return new ExpenseQueryTool.Query(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"),
                "summary", groups, filters, "amount_desc", 10);
    }
    @Test void bindsUserAndValuesAndKeepsOwnershipOutsideModelControl() {
        var plan = ExpenseQueryTool.plan(42L, query(List.of("category", "month"),
                List.of(new ExpenseQueryTool.Filter("merchant", "contains", "x%' OR 1=1 --"))));
        assertThat(plan.sql()).contains("t.user_id = :owner", "t.deleted_at IS NULL", "m.user_id = t.user_id", "GROUP BY")
                .doesNotContain("OR 1=1");
        assertThat(plan.parameters().getValue("owner")).isEqualTo(42L);
        assertThat(plan.parameters().getValue("f0")).isEqualTo("%x!%' or 1=1 --%");
    }
    @Test void rejectsIdentifiersAndUnsupportedOperators() {
        assertThatThrownBy(() -> ExpenseQueryTool.plan(1L, query(List.of("user_id"), List.of())))
                .isInstanceOf(WebApiException.class);
        assertThatThrownBy(() -> ExpenseQueryTool.plan(1L, query(List.of(), List.of(new ExpenseQueryTool.Filter("category", "gte", "A")))))
                .isInstanceOf(WebApiException.class);
        assertThatThrownBy(() -> ExpenseQueryTool.plan(1L, query(List.of(), List.of(new ExpenseQueryTool.Filter("amount", "eq", "0; delete")))))
                .isInstanceOf(WebApiException.class);
    }
    @Test void rejectsUnboundedQueries() {
        assertThatThrownBy(() -> ExpenseQueryTool.plan(1L, new ExpenseQueryTool.Query(LocalDate.parse("2020-01-01"),
                LocalDate.parse("2026-01-01"), "summary", List.of(), List.of(), "amount_desc", 10)))
                .isInstanceOf(WebApiException.class);
        assertThatThrownBy(() -> ExpenseQueryTool.plan(1L, new ExpenseQueryTool.Query(LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-01"), "summary", List.of(), List.of(), "amount_desc", 10)))
                .isInstanceOf(WebApiException.class);
    }
}
