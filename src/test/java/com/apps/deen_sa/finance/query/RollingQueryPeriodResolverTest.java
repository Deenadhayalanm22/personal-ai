package com.apps.deen_sa.finance.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RollingQueryPeriodResolverTest {
    @Test
    void originalTextOverridesClosedClassifierPeriod() {
        assertThat(RollingQueryPeriodResolver.resolve("LAST_7_DAYS", "How much did I spend in the last 5 weeks?"))
                .isEqualTo("LAST_5_WEEKS");
        assertThat(RollingQueryPeriodResolver.resolve("LAST_7_DAYS", "spending for last 1 months"))
                .isEqualTo("LAST_1_MONTH");
    }

    @Test
    void keepsClassifiedPeriodWhenNoRollingPeriodWasStated() {
        assertThat(RollingQueryPeriodResolver.resolve("THIS_MONTH", "How much this month?"))
                .isEqualTo("THIS_MONTH");
    }
}
