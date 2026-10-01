package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentRecurrenceUnit;
import com.apps.deen_sa.domain.RecurringCommitmentOccurrenceStatus;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.RecurringCommitmentOccurrenceEntity;
import com.apps.deen_sa.entity.UserRecurringCommitmentEntity;
import com.apps.deen_sa.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MonthlyCommitmentCompletionProgressTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.apps.deen_sa.domain.InvestmentAssetType.class, names = {"MUTUAL_FUND", "STOCK"})
    void skippedInvestmentsAreExcludedOnlyForTheirScheduledMonth(com.apps.deen_sa.domain.InvestmentAssetType type) {
        var stored = mock(MonthlyFinancialSnapshotRepository.class);
        var loans = mock(UserLoanRepository.class);
        var investments = mock(UserInvestmentRepository.class);
        var outcomes = mock(InvestmentTransactionRepository.class);
        var user = new AppUserEntity(); user.setId(9L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        var investment = new com.apps.deen_sa.entity.UserInvestmentEntity();
        investment.setId(4L); investment.setAssetType(type); investment.setDisplayNameSnapshot("Monthly investment");
        investment.setSipStatus(com.apps.deen_sa.domain.InvestmentSipStatus.ACTIVE);
        investment.setSipAmount(new BigDecimal("45000.00")); investment.setSipDay(1);
        investment.setSipStartMonth(LocalDate.of(2026, 1, 1));
        when(investments.findByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(investment));
        var outcome = new com.apps.deen_sa.entity.InvestmentTransactionEntity();
        outcome.setStatus(com.apps.deen_sa.domain.InvestmentTransactionStatus.SKIPPED);
        when(outcomes.findByInvestmentIdAndTransactionKindAndScheduledMonth(4L,
                com.apps.deen_sa.domain.InvestmentTransactionKind.SIP, LocalDate.of(2026, 10, 1)))
                .thenReturn(java.util.Optional.of(outcome));
        var clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        var projection = new MonthlyFinancialSnapshotService(stored, loans, investments, clock);
        ReflectionTestUtils.setField(projection, "investmentOccurrences", outcomes);
        var legacy = new com.apps.deen_sa.entity.MonthlyFinancialSnapshotEntity(); legacy.setCalculationVersion(10);
        when(stored.findByUserIdAndScopeMonth(9L, LocalDate.of(2026, 10, 1))).thenReturn(java.util.Optional.of(legacy));
        var current = projection.current(user);
        assertThat(current.fullIntendedCommitment()).isEqualByComparingTo("0");
        assertThat(current.commitmentBuckets()).allSatisfy(bucket -> assertThat(bucket.sources()).isEmpty());
        var overview = new MonthlyPaymentOverviewService(projection, mock(RecurringCommitmentOccurrenceRepository.class),
                mock(LoanEmiOccurrenceRepository.class), outcomes, clock).forMonth(user, YearMonth.of(2026, 10));
        assertThat(overview.plannedInvesting()).isEqualByComparingTo("0");
        assertThat(projection.preview(user, YearMonth.of(2026, 11)).fullIntendedCommitment()).isEqualByComparingTo("45000");
        outcome.setStatus(com.apps.deen_sa.domain.InvestmentTransactionStatus.CONFIRMED);
        assertThat(projection.preview(user, YearMonth.of(2026, 10)).fullIntendedCommitment()).isEqualByComparingTo("45000");
        verify(stored).save(legacy);
    }

    @Test
    void completedMonthlyItemStaysInCurrentPlanAndReportsFullProgress() {
        var snapshots = mock(MonthlyFinancialSnapshotRepository.class);
        var loans = mock(UserLoanRepository.class);
        var investments = mock(UserInvestmentRepository.class);
        var commitments = mock(UserRecurringCommitmentRepository.class);
        var occurrences = mock(RecurringCommitmentOccurrenceRepository.class);
        var user = new AppUserEntity(); user.setId(9L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        var commitment = new UserRecurringCommitmentEntity();
        commitment.setId(3L); commitment.setUser(user); commitment.setLabel("Internet bill");
        commitment.setPlanningAmount(new BigDecimal("5000.00"));
        commitment.setEffectiveMonth(LocalDate.of(2026, 1, 1));
        commitment.setRecurrenceUnit(CommitmentRecurrenceUnit.MONTH);
        commitment.setNextExpectedDate(LocalDate.of(2026, 11, 5));
        var occurrence = new RecurringCommitmentOccurrenceEntity();
        occurrence.setCommitment(commitment); occurrence.setScheduledMonth(LocalDate.of(2026, 10, 1));
        occurrence.setCompletedAt(LocalDate.of(2026, 10, 5));
        occurrence.setActualAmount(new BigDecimal("4800.00"));
        occurrence.setStatus(RecurringCommitmentOccurrenceStatus.COMPLETED);
        when(loans.findByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
        when(investments.findByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
        when(commitments.findAllOwned(9L)).thenReturn(List.of(commitment));
        when(occurrences.findByCommitmentIdAndScheduledMonthBetweenOrderByScheduledMonthAsc(3L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(List.of(occurrence));
        when(occurrences.findByCommitmentIdAndScheduledMonth(3L, LocalDate.of(2026, 10, 1)))
                .thenReturn(java.util.Optional.of(occurrence));
        var clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        var projection = new MonthlyFinancialSnapshotService(snapshots, loans, investments,
                commitments, null, null, clock);
        ReflectionTestUtils.setField(projection, "commitmentOccurrences", occurrences);

        var current = projection.preview(user, YearMonth.of(2026, 10));
        var essential = current.commitmentBuckets().stream()
                .filter(bucket -> bucket.key().equals("ESSENTIAL_LIVING")).findFirst().orElseThrow();
        assertThat(essential.plannedAmount()).isEqualByComparingTo("5000.00");
        assertThat(essential.sources()).hasSize(1);

        var copy = mock(CommitmentCopyGenerator.class);
        when(copy.generateCommitmentRunway(any(), any())).thenAnswer(call -> call.getArgument(1));
        var snapshotService = mock(MonthlyFinancialSnapshotService.class);
        when(snapshotService.current(user)).thenReturn(current);
        var next = projection.preview(user, YearMonth.of(2026, 11));
        when(snapshotService.next(user)).thenReturn(next);
        var reader = new MonthlyCommitmentCardService(snapshotService, copy, clock);
        ReflectionTestUtils.setField(reader, "commitmentOccurrences", occurrences);
        var card = reader.currentFor(user).cards().getFirst();
        assertThat(card.components()).filteredOn(item -> item.label().equals("Essential living"))
                .extracting(item -> item.value()).containsExactly(new BigDecimal("5000.00"));
        assertThat(card.components()).filteredOn(item -> item.label().equals("Recurring commitments complete"))
                .extracting(item -> item.value()).containsExactly(new BigDecimal("5000.00"));

        occurrence.setStatus(RecurringCommitmentOccurrenceStatus.SKIPPED);
        commitment.setNextExpectedDate(LocalDate.of(2026, 10, 5));
        assertThat(projection.preview(user, YearMonth.of(2026, 10)).fullIntendedCommitment()).isEqualByComparingTo("0");
        commitment.setNextExpectedDate(LocalDate.of(2026, 11, 5));
        assertThat(projection.preview(user, YearMonth.of(2026, 11)).fullIntendedCommitment()).isEqualByComparingTo("5000");
    }
}
