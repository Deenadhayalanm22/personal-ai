package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.entity.AppUserEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class CommitmentSalaryInsightRuleTest {
    private final CommitmentSalaryInsightRule rule = new CommitmentSalaryInsightRule();

    @Test
    void exactSalaryQualifiesAPreciseCommitmentLens() {
        StoryContext context = context();
        context.put("salary.visibility", "EXACT");
        context.put("salary.exactMonthly", new BigDecimal("80000"));

        StoryInsight insight = rule.qualify(request(), context).orElseThrow();

        assertThat(insight.key()).isEqualTo("COMMITMENT_INCOME_EXACT");
        assertThat((BigDecimal) insight.facts().get("percent")).isEqualByComparingTo("57.5");
    }

    @Test
    void salaryRangeQualifiesWithoutInventingAPercentage() {
        StoryContext context = context();
        context.put("salary.visibility", "RANGE");
        context.put("salary.range", "FROM_50000_TO_100000");

        StoryInsight insight = rule.qualify(request(), context).orElseThrow();

        assertThat(insight.key()).isEqualTo("COMMITMENT_INCOME_RANGE");
        assertThat(insight.facts()).containsOnly(entry("range", "FROM_50000_TO_100000"));
    }

    private StoryContext context() { return new StoryContext(Map.of("commitment.total", new BigDecimal("46000"))); }
    private StoryEnrichmentRequest request() {
        AppUserEntity user = new AppUserEntity(); user.setId(7L);
        return new StoryEnrichmentRequest(user, MoneyStoryType.MONTHLY_COMMITMENT, YearMonth.of(2026, 10), Map.of("commitment.total", new BigDecimal("46000")));
    }
}
