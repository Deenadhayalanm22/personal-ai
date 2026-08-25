package com.apps.deen_sa.finance.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPeriodLabelFormatterTest {
    @Test
    void describesAnySupportedRollingCombination() {
        assertThat(QueryPeriodLabelFormatter.describe("LAST_10_DAYS")).isEqualTo("in the last 10 days");
        assertThat(QueryPeriodLabelFormatter.describe("LAST_4_WEEKS")).isEqualTo("in the last 4 weeks");
        assertThat(QueryPeriodLabelFormatter.describe("LAST_6_MONTHS")).isEqualTo("in the last 6 months");
        assertThat(QueryPeriodLabelFormatter.describe("LAST_2_YEARS")).isEqualTo("in the last 2 years");
    }

    @Test
    void normalizesSingularAndPluralUnits() {
        assertThat(QueryPeriodLabelFormatter.describe("LAST_DAY")).isEqualTo("in the last 1 day");
        assertThat(QueryPeriodLabelFormatter.describe("LAST_1_WEEKS")).isEqualTo("in the last 1 week");
    }

    @Test
    void preservesFriendlyCalendarLabelsAndHandlesUnknownInput() {
        assertThat(QueryPeriodLabelFormatter.describe("THIS_MONTH")).isEqualTo("this month");
        assertThat(QueryPeriodLabelFormatter.describe("CUSTOM")).isEqualTo("for the requested period");
    }
}
