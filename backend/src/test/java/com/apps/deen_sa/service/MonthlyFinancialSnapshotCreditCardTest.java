package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserCreditCardEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonthlyFinancialSnapshotCreditCardTest {
    @Test
    void projectsOnlyTheStatementPeriodBeforeTheDueMonth() {
        MonthlyFinancialSnapshotRepository snapshots = mock(MonthlyFinancialSnapshotRepository.class);
        UserLoanRepository loans = mock(UserLoanRepository.class);
        UserInvestmentRepository investments = mock(UserInvestmentRepository.class);
        UserRecurringCommitmentRepository recurring = mock(UserRecurringCommitmentRepository.class);
        UserCreditCardRepository cards = mock(UserCreditCardRepository.class);
        FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
        AppUserEntity user = new AppUserEntity(); user.setId(9L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        UserReferenceEntity account = new UserReferenceEntity(); account.setId(31L); account.setCanonicalName("HDFC credit card");
        UserCreditCardEntity card = new UserCreditCardEntity(); card.setId(4L); card.setAccountReference(account); card.setCardName("Millennia"); card.setIssuerName("HDFC Bank"); card.setStatementDay(18); card.setDueDay(5);
        when(snapshots.findByUserIdAndScopeMonth(anyLong(), any())).thenReturn(Optional.empty());
        when(loans.findByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of()); when(investments.findByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
        when(recurring.findAllOwned(9L)).thenReturn(List.of()); when(cards.findByUserIdAndActiveTrueOrderByCreatedAtDesc(9L)).thenReturn(List.of(card));
        when(transactions.sumVisibleByAccountAndPeriod(9L, 31L, LocalDate.of(2026, 7, 18), LocalDate.of(2026, 8, 18))).thenReturn(new BigDecimal("12500.00"));
        var service = new MonthlyFinancialSnapshotService(snapshots, loans, investments, recurring, cards, transactions,
                Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneId.of("Asia/Kolkata")));

        var preview = service.preview(user, YearMonth.of(2026, 9));
        verify(snapshots, never()).save(any());
        var snapshot = service.current(user);
        assertThat(preview).isEqualTo(snapshot);

        var bill = snapshot.commitmentBuckets().stream().filter(bucket -> bucket.key().equals("CREDIT_CARD_BILLS")).findFirst().orElseThrow();
        assertThat(bill.plannedAmount()).isEqualByComparingTo("12500.00");
        assertThat(bill.sources().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(bill.sources().getFirst().detail()).contains("2026-07-18 to 2026-08-17");
    }
    @Test void firstDayPurchaseBelongsToNextMonthsBillAndRefreshesLegacySnapshots() {
        var snapshots = mock(MonthlyFinancialSnapshotRepository.class);
        var loans = mock(UserLoanRepository.class); var investments = mock(UserInvestmentRepository.class);
        var recurring = mock(UserRecurringCommitmentRepository.class); var cards = mock(UserCreditCardRepository.class);
        var transactions = mock(FinancialTransactionRepository.class);
        var user = new AppUserEntity(); user.setId(9L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        var account = new UserReferenceEntity(); account.setId(31L);
        var card = new UserCreditCardEntity(); card.setId(4L); card.setAccountReference(account);
        card.setCardName("Millennia"); card.setIssuerName("HDFC"); card.setStatementDay(1); card.setDueDay(21);
        when(cards.findByUserIdAndActiveTrueOrderByCreatedAtDesc(9L)).thenReturn(List.of(card));
        when(transactions.sumVisibleByAccountAndPeriod(eq(9L), eq(31L), any(), any())).thenAnswer(call -> {
            LocalDate start=call.getArgument(2), end=call.getArgument(3);
            LocalDate purchase=LocalDate.of(2026,10,1);
            return !purchase.isBefore(start) && purchase.isBefore(end) ? new BigDecimal("480") : BigDecimal.ZERO;
        });
        var legacy = new java.util.HashMap<LocalDate, com.apps.deen_sa.entity.MonthlyFinancialSnapshotEntity>();
        when(snapshots.findByUserIdAndScopeMonth(eq(9L), any())).thenAnswer(call -> Optional.of(
                legacy.computeIfAbsent(call.getArgument(1), date -> {
                    var old = new com.apps.deen_sa.entity.MonthlyFinancialSnapshotEntity();
                    old.setCalculationVersion(12); old.setScopeMonth(date); old.setUser(user); return old;
                })));
        var service = new MonthlyFinancialSnapshotService(snapshots,loans,investments,recurring,cards,transactions,
                Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"),ZoneOffset.UTC));
        var october=service.current(user); var november=service.next(user);
        assertThat(october.fullIntendedCommitment()).isZero();
        assertThat(november.fullIntendedCommitment()).isEqualByComparingTo("480");
        assertThat(november.commitmentBuckets().stream().filter(b -> b.key().equals("CREDIT_CARD_BILLS"))
                .findFirst().orElseThrow().sources().getFirst().dueDate()).isEqualTo(LocalDate.of(2026,11,21));
        verify(transactions).sumVisibleByAccountAndPeriod(9L,31L,LocalDate.of(2026,9,1),LocalDate.of(2026,10,1));
        verify(transactions).sumVisibleByAccountAndPeriod(9L,31L,LocalDate.of(2026,10,1),LocalDate.of(2026,11,1));
        assertThat(CreditCardBillService.periodStart(card,YearMonth.of(2026,12))).isEqualTo(LocalDate.of(2026,11,1));
        assertThat(CreditCardBillService.statementEnd(card,YearMonth.of(2026,12))).isEqualTo(LocalDate.of(2026,11,30));
        assertThat(CreditCardBillService.statementEnd(card,YearMonth.of(2028,3))).isEqualTo(LocalDate.of(2028,2,29));
    }
    @Test void startsIncludingBillsInTheConfiguredDueMonth() {
        var snapshots=mock(MonthlyFinancialSnapshotRepository.class);
        var cards=mock(UserCreditCardRepository.class);var transactions=mock(FinancialTransactionRepository.class);
        var user=new AppUserEntity();user.setId(9L);user.setCurrency("INR");user.setTimezone("Asia/Kolkata");
        var ref=new UserReferenceEntity();ref.setId(31L);
        var card=new UserCreditCardEntity();card.setId(4L);card.setAccountReference(ref);card.setCardName("Visa");card.setIssuerName("HDFC");card.setStatementDay(1);card.setDueDay(21);card.setStartMonth(LocalDate.of(2026,11,1));
        when(cards.findByUserIdAndActiveTrueOrderByCreatedAtDesc(9L)).thenReturn(List.of(card));
        when(transactions.sumVisibleByAccountAndPeriod(eq(9L),eq(31L),any(),any())).thenReturn(new BigDecimal("480"));
        var service=new MonthlyFinancialSnapshotService(snapshots,mock(UserLoanRepository.class),mock(UserInvestmentRepository.class),mock(UserRecurringCommitmentRepository.class),cards,transactions,Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"),ZoneOffset.UTC));
        assertThat(service.current(user).fullIntendedCommitment()).isZero();
        verifyNoInteractions(transactions);
        assertThat(service.next(user).fullIntendedCommitment()).isEqualByComparingTo("480");
        verify(transactions).sumVisibleByAccountAndPeriod(9L,31L,LocalDate.of(2026,10,1),LocalDate.of(2026,11,1));
    }

}
