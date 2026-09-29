package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserIncomeProfileRepository;
import com.apps.deen_sa.service.MonthlyFinancialSnapshotService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthlyPlanningToolTest {
    private final AppUserEntity user = new AppUserEntity();
    private final MonthlyFinancialSnapshotService snapshots = mock(MonthlyFinancialSnapshotService.class);
    private final UserIncomeProfileRepository incomes = mock(UserIncomeProfileRepository.class);
    private final MonthlyPlanningTool tool = new MonthlyPlanningTool(snapshots, incomes,
            Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC));
    MonthlyPlanningToolTest() { user.setId(42L); }
    private void fixture(String salaryVisibility) {
        var loan = new MonthlyFinancialSnapshotService.Source("LOAN", "1", "Home loan", new BigDecimal("30000"),
                LocalDate.parse("2026-10-05"), "Loan EMI", "Lender", null, null, null);
        var sip = new MonthlyFinancialSnapshotService.Source("MUTUAL_FUND_SIP", "2", "Index SIP", new BigDecimal("20000"),
                LocalDate.parse("2026-10-12"), "Mutual fund SIP", "Active", null, null, null);
        var living = new MonthlyFinancialSnapshotService.Source("RECURRING_COMMITMENT", "3", "Rent", new BigDecimal("25000"),
                LocalDate.parse("2026-10-01"), "Housing", "Planning amount", null, null, null);
        var value = new MonthlyFinancialSnapshotService.MonthlySnapshot("2026-10", "INR", 8, new BigDecimal("75000"), List.of(
                new MonthlyFinancialSnapshotService.Bucket("DEBT_REPAYMENTS", "Debt repayments", new BigDecimal("30000"), List.of(loan)),
                new MonthlyFinancialSnapshotService.Bucket("PLANNED_INVESTING", "Planned investing", new BigDecimal("20000"), List.of(sip)),
                new MonthlyFinancialSnapshotService.Bucket("ESSENTIAL_LIVING", "Essential living", new BigDecimal("25000"), List.of(living))));
        when(snapshots.preview(user, YearMonth.of(2026, 10))).thenReturn(value);
        var income = new UserIncomeProfileEntity(); income.setUserId(user.getId()); income.setSalaryVisibility(salaryVisibility);
        income.setExactMonthlySalary(new BigDecimal("60000")); income.setSalaryFrequency("MONTHLY");
        when(incomes.findById(user.getId())).thenReturn(Optional.of(income));
    }
    @Test void comparesCanonicalPlanAndRecalculatesOnlySelectedSource() {
        fixture("EXACT");
        var baseline = tool.read(user, new MonthlyPlanningTool.MonthRequest("2026-10"));
        assertThat(baseline.baselineTotal()).isEqualByComparingTo("75000");
        assertThat(baseline.baselineAfterIncome()).isEqualByComparingTo("-15000");
        var result = tool.simulate(user, new MonthlyPlanningTool.ScenarioRequest("2026-10", List.of(
                new MonthlyPlanningTool.Adjustment("MUTUAL_FUND_SIP:2:2026-10-12", new BigDecimal("5000")))));
        assertThat(result.proposedTotal()).isEqualByComparingTo("60000");
        assertThat(result.proposedAfterIncome()).isEqualByComparingTo("0");
        assertThat(result.items()).filteredOn(item -> item.sourceType().equals("LOAN")).singleElement()
                .extracting(MonthlyPlanningTool.Item::proposed).isEqualTo(new BigDecimal("30000"));
        verify(snapshots, never()).refreshCurrent(any());
    }
    @Test void rejectsLoanReductionsUnknownSourcesAndFutureMonths() {
        fixture("EXACT");
        assertThatThrownBy(() -> tool.simulate(user, new MonthlyPlanningTool.ScenarioRequest("2026-10", List.of(
                new MonthlyPlanningTool.Adjustment("LOAN:1:2026-10-05", BigDecimal.ZERO)))))
                .isInstanceOf(WebApiException.class);
        assertThatThrownBy(() -> tool.simulate(user, new MonthlyPlanningTool.ScenarioRequest("2026-10", List.of(
                new MonthlyPlanningTool.Adjustment("MUTUAL_FUND_SIP:99:2026-10-12", BigDecimal.ZERO)))))
                .isInstanceOf(WebApiException.class);
        assertThatThrownBy(() -> tool.read(user, new MonthlyPlanningTool.MonthRequest("2026-12")))
                .isInstanceOf(WebApiException.class);
    }
    @Test void separateSavingsTargetsKeepDistinctScenarioKeys() {
        var first = new MonthlyFinancialSnapshotService.Source("COMMITMENT_SAVINGS", "3", "Insurance savings",
                new BigDecimal("2000"), LocalDate.parse("2026-10-01"), "Saving", "For payment on 2027-09-01", null, null, null);
        var second = new MonthlyFinancialSnapshotService.Source("COMMITMENT_SAVINGS", "3", "Insurance savings",
                new BigDecimal("1000"), LocalDate.parse("2026-10-01"), "Saving", "For payment on 2028-09-01", null, null, null);
        when(snapshots.preview(user, YearMonth.of(2026, 10))).thenReturn(new MonthlyFinancialSnapshotService.MonthlySnapshot(
                "2026-10", "INR", 8, new BigDecimal("3000"), List.of(new MonthlyFinancialSnapshotService.Bucket(
                "COMMITMENT_SAVINGS", "Savings", new BigDecimal("3000"), List.of(first, second)))));
        var baseline = tool.read(user, new MonthlyPlanningTool.MonthRequest("2026-10"));
        assertThat(baseline.items()).extracting(MonthlyPlanningTool.Item::sourceKey).doesNotHaveDuplicates();
        var scenario = tool.simulate(user, new MonthlyPlanningTool.ScenarioRequest("2026-10", List.of(
                new MonthlyPlanningTool.Adjustment(baseline.items().getFirst().sourceKey(), BigDecimal.ZERO))));
        assertThat(scenario.proposedTotal()).isEqualByComparingTo("1000");
    }
    @Test void rangeSalaryDoesNotYieldAnInventedNumericGap() {
        fixture("RANGE");
        var result = tool.read(user, new MonthlyPlanningTool.MonthRequest("2026-10"));
        assertThat(result.incomeStatus()).isEqualTo("RANGE_ONLY");
        assertThat(result.baselineAfterIncome()).isNull();
    }
}
