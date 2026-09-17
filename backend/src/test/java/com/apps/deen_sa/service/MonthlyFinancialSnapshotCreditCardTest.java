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
        when(transactions.sumVisibleByAccountAndPeriod(9L, 31L, LocalDate.of(2026, 7, 19), LocalDate.of(2026, 8, 19))).thenReturn(new BigDecimal("12500.00"));
        var service = new MonthlyFinancialSnapshotService(snapshots, loans, investments, recurring, cards, transactions,
                Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneId.of("Asia/Kolkata")));

        var snapshot = service.current(user);

        var bill = snapshot.commitmentBuckets().stream().filter(bucket -> bucket.key().equals("CREDIT_CARD_BILLS")).findFirst().orElseThrow();
        assertThat(bill.plannedAmount()).isEqualByComparingTo("12500.00");
        assertThat(bill.sources().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(bill.sources().getFirst().detail()).contains("2026-07-19 to 2026-08-18");
    }
}
