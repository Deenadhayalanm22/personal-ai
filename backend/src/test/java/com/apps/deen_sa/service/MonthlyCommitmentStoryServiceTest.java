package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserIncomeProfileEntity;
import com.apps.deen_sa.entity.InvestmentTransactionEntity;
import com.apps.deen_sa.domain.InvestmentTransactionKind;
import com.apps.deen_sa.domain.InvestmentTransactionStatus;
import com.apps.deen_sa.repository.InvestmentTransactionRepository;
import com.apps.deen_sa.repository.UserIncomeProfileRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class MonthlyCommitmentStoryServiceTest {
    @Test
    void totalsOnlyCommitmentsActiveInTheCurrentMonth() {
        MonthlyFinancialSnapshotService snapshots = mock(MonthlyFinancialSnapshotService.class);
        MoneyStoryCopyGenerator copy = mock(MoneyStoryCopyGenerator.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(7L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        var october = new MonthlyFinancialSnapshotService.MonthlySnapshot("2026-10", "INR", 3,
                new BigDecimal("46000"), List.of(
                new MonthlyFinancialSnapshotService.Bucket("DEBT_REPAYMENTS", "Debt repayments", new BigDecimal("26000"), List.of(
                        new MonthlyFinancialSnapshotService.Source("LOAN", "1", "Home loan", new BigDecimal("26000"), null, "Loan EMI", "Bank", 24, "2028-09", "2028-10"))),
                new MonthlyFinancialSnapshotService.Bucket("PLANNED_INVESTING", "Planned investing", new BigDecimal("20000"), List.of(
                        new MonthlyFinancialSnapshotService.Source("MUTUAL_FUND_SIP", "2", "Fund", new BigDecimal("20000"), null, "Mutual fund SIP", "Active SIP", null, null, null)))));
        var november = new MonthlyFinancialSnapshotService.MonthlySnapshot("2026-11", "INR", 3,
                new BigDecimal("46000"), october.commitmentBuckets());
        when(snapshots.current(user)).thenReturn(october);
        when(snapshots.next(user)).thenReturn(november);

        when(copy.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(copy.generateCommitmentRunway(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        var story = new MonthlyCommitmentStoryService(snapshots, copy,
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneId.of("Asia/Kolkata"))).currentFor(user);

        assertThat(story.storyType()).isEqualTo("MONTHLY_COMMITMENT");
        assertThat(story.storyId()).isEqualTo("monthly-commitment");
        assertThat(story.cardFace().displayValue()).contains("46,000");
        assertThat(story.cards().getFirst().components()).extracting(component -> component.label())
                .containsExactly("Debt repayments", "Planned investing");
        assertThat(story.cards().getFirst().layout()).isEqualTo("COMMITMENT");
        assertThat(story.cards()).hasSize(3);
        assertThat(story.cards().get(1).cardId()).isEqualTo("next-commitment");
        assertThat(story.cards().get(1).eyebrow()).isEqualTo("NEXT MONTH · READY");
        assertThat(story.cards().getFirst().body()).contains("free from October 2028");
        var currentEvidence = story.evidence().byCard().get("commitment");
        assertThat(currentEvidence.transactions()).hasSize(2);
        assertThat(currentEvidence.transactions()).extracting(row -> row.categoryLabel())
                .containsExactly("Loan EMI · 👾 Long-game member · ends September 2028",
                        "Mutual fund SIP · 🌱 Future-you contribution");
        assertThat(story.cards().getFirst().actions()).extracting(action -> action.type()).containsExactly("OPEN_EVIDENCE");
        assertThat(story.cards().get(2).actions()).extracting(action -> action.type()).containsExactly("OPEN_SALARY_OUTLOOK");
        assertThat(story.evidence().byCard()).containsKeys("commitment", "next-commitment");
    }

    @Test
    void exactMonthlySalaryAddsAPeriodSpecificPercentageToBothCommitmentCardsWithoutAStandaloneCard() {
        MonthlyFinancialSnapshotService snapshots = mock(MonthlyFinancialSnapshotService.class);
        MoneyStoryCopyGenerator copy = mock(MoneyStoryCopyGenerator.class);
        UserIncomeProfileRepository profiles = mock(UserIncomeProfileRepository.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(7L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        UserIncomeProfileEntity income = new UserIncomeProfileEntity();
        income.setSalaryVisibility("EXACT"); income.setExactMonthlySalary(new BigDecimal("80000")); income.setSalaryFrequency("MONTHLY");
        when(profiles.findById(7L)).thenReturn(Optional.of(income));
        var september = snapshot("2026-09", new BigDecimal("62510"));
        var october = snapshot("2026-10", new BigDecimal("107510"));
        when(snapshots.current(user)).thenReturn(september);
        when(snapshots.next(user)).thenReturn(october);
        when(copy.generateCommitmentRunway(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        var pipeline = new StoryEnrichmentPipeline(List.of(new SalaryStoryContextContributor(profiles)), List.of(new CommitmentSalaryInsightRule()));

        var story = new MonthlyCommitmentStoryService(snapshots, copy,
                Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneId.of("Asia/Kolkata")), pipeline).currentFor(user);

        assertThat(story.cards()).hasSize(2);
        assertThat(story.cards().getFirst().components()).filteredOn(component -> component.label().equals("Of monthly salary"))
                .extracting(component -> component.displayValue()).containsExactly("78.1%");
        assertThat(story.cards().get(1).components()).filteredOn(component -> component.label().equals("Of monthly salary"))
                .extracting(component -> component.displayValue()).containsExactly("134.4%");
        assertThat(story.cards()).extracting(card -> card.cardId()).doesNotContain("commitment-income");
    }

    @Test
    void showsConfirmedSipAllocationsAsReadOnlyProgressAndLinksToInvestments() {
        MonthlyFinancialSnapshotService snapshots = mock(MonthlyFinancialSnapshotService.class);
        MoneyStoryCopyGenerator copy = mock(MoneyStoryCopyGenerator.class);
        InvestmentTransactionRepository investmentTransactions = mock(InvestmentTransactionRepository.class);
        AppUserEntity user = new AppUserEntity(); user.setId(7L); user.setCurrency("INR");
        var month = new MonthlyFinancialSnapshotService.MonthlySnapshot("2026-09", "INR", 1, new BigDecimal("10000"), List.of(
                new MonthlyFinancialSnapshotService.Bucket("DEBT_REPAYMENTS", "Debt repayments", BigDecimal.ZERO, List.of()),
                new MonthlyFinancialSnapshotService.Bucket("PLANNED_INVESTING", "Planned investing", new BigDecimal("10000"), List.of(
                        new MonthlyFinancialSnapshotService.Source("MUTUAL_FUND_SIP", "2", "Fund", new BigDecimal("10000"), null, "Mutual fund SIP", "Active SIP", null, null, null))),
                new MonthlyFinancialSnapshotService.Bucket("ESSENTIAL_LIVING", "Essential living", BigDecimal.ZERO, List.of()),
                new MonthlyFinancialSnapshotService.Bucket("CREDIT_CARD_BILLS", "Credit-card bills", BigDecimal.ZERO, List.of())));
        InvestmentTransactionEntity confirmed = new InvestmentTransactionEntity(); confirmed.setAmount(new BigDecimal("10000"));
        when(snapshots.current(user)).thenReturn(month); when(snapshots.next(user)).thenReturn(month);
        when(investmentTransactions.findByInvestmentIdInAndTransactionKindAndScheduledMonthAndStatus(eq(List.of(2L)),
                eq(InvestmentTransactionKind.SIP), eq(java.time.LocalDate.of(2026, 9, 1)), eq(InvestmentTransactionStatus.CONFIRMED))).thenReturn(List.of(confirmed));
        when(copy.generateCommitmentRunway(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var story = new MonthlyCommitmentStoryService(snapshots, copy, Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneId.of("Asia/Kolkata")),
                new StoryEnrichmentPipeline(List.of(), List.of()), null, investmentTransactions).currentFor(user);

        assertThat(story.cards().getFirst().components()).filteredOn(component -> component.label().equals("SIP allocations complete"))
                .extracting(component -> component.displayValue()).containsExactly("₹10,000.00");
        assertThat(story.cards().getFirst().actions()).extracting(action -> action.label()).contains("Review investments");
    }

    private MonthlyFinancialSnapshotService.MonthlySnapshot snapshot(String month, BigDecimal total) {
        return new MonthlyFinancialSnapshotService.MonthlySnapshot(month, "INR", 1, total, List.of(
                new MonthlyFinancialSnapshotService.Bucket("DEBT_REPAYMENTS", "Debt repayments", total, List.of(
                        new MonthlyFinancialSnapshotService.Source("LOAN", "1", "Loan", total, null, "Loan EMI", "Bank", 24, "2028-09", "2028-10"))),
                new MonthlyFinancialSnapshotService.Bucket("PLANNED_INVESTING", "Planned investing", BigDecimal.ZERO, List.of())));
    }
}
