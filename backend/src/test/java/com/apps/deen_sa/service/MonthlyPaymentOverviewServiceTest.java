package com.apps.deen_sa.service;
import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthlyPaymentOverviewServiceTest {
    @Test void includesPendingInvestmentsInStillToPayAndRemovesConfirmedOrSkippedPlans() {
        var snapshots=mock(MonthlyFinancialSnapshotService.class); var occurrences=mock(RecurringCommitmentOccurrenceRepository.class);
        var loans=mock(LoanEmiOccurrenceRepository.class); var investments=mock(InvestmentTransactionRepository.class);
        var u=new AppUserEntity();u.setTimezone("Asia/Kolkata");var month=YearMonth.of(2026,10);
        var paid=new LoanEmiOccurrenceEntity();paid.setStatus(LoanEmiOccurrenceStatus.PAID);
        when(loans.findByLoanIdAndDueMonth(1L,month.atDay(1))).thenReturn(Optional.of(paid));
        var done=new InvestmentTransactionEntity();done.setStatus(InvestmentTransactionStatus.CONFIRMED);
        var skipped=new InvestmentTransactionEntity();skipped.setStatus(InvestmentTransactionStatus.SKIPPED);
        when(investments.findByInvestmentIdAndTransactionKindAndScheduledMonth(2L,InvestmentTransactionKind.SIP,month.atDay(1))).thenReturn(Optional.of(done));
        when(investments.findByInvestmentIdAndTransactionKindAndScheduledMonth(3L,InvestmentTransactionKind.SIP,month.atDay(1))).thenReturn(Optional.of(skipped));
        when(snapshots.preview(u,month)).thenReturn(new MonthlyFinancialSnapshotService.MonthlySnapshot("2026-10","INR",9,BigDecimal.ZERO,List.of(
            bucket("DEBT_REPAYMENTS",source("LOAN","1",500)),bucket("CREDIT_CARD_BILLS",source("CREDIT_CARD_BILL","7",300)),
            bucket("PLANNED_INVESTING",source("MUTUAL_FUND_SIP","2",1000),source("STOCK_MONTHLY_PLAN","3",2000),source("MUTUAL_FUND_SIP","4",400),source("STOCK_MONTHLY_PLAN","6",10000)),
            bucket("COMMITMENT_SAVINGS",source("COMMITMENT_SAVINGS","5",200)))));
        var service=new MonthlyPaymentOverviewService(snapshots,occurrences,loans,investments,Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC));
        var cardBills = mock(CreditCardBillService.class); service.cardBills = cardBills;
        when(cardBills.paid(u, 7L, month)).thenReturn(BigDecimal.ZERO);
        var result=service.forMonth(u,month);
        assertThat(result.stillToPay()).isEqualByComparingTo("10700");assertThat(result.plannedInvesting()).isEqualByComparingTo("10400");assertThat(result.plannedSavings()).isEqualByComparingTo("200");
        for (var status : List.of(InvestmentTransactionStatus.CONFIRMED, InvestmentTransactionStatus.SKIPPED)) {
            var decided = new InvestmentTransactionEntity(); decided.setStatus(status);
            when(investments.findByInvestmentIdAndTransactionKindAndScheduledMonth(6L, InvestmentTransactionKind.SIP, month.atDay(1)))
                    .thenReturn(Optional.of(decided));
            var updated = service.forMonth(u, month);
            assertThat(updated.stillToPay()).isEqualByComparingTo("700");
            assertThat(updated.plannedInvesting()).isEqualByComparingTo("400");
            assertThat(updated.plannedSavings()).isEqualByComparingTo("200");
        }
        when(cardBills.paid(u, 7L, month)).thenReturn(new BigDecimal("100"));
        assertThat(service.forMonth(u,month).stillToPay()).isEqualByComparingTo("600");
        when(cardBills.paid(u, 7L, month)).thenReturn(new BigDecimal("350"));
        assertThat(service.forMonth(u,month).stillToPay()).isEqualByComparingTo("400");
        assertThat(service.forMonth(u,month.minusMonths(1))).isNull();
    }
    private MonthlyFinancialSnapshotService.Source source(String type,String id,int amount){return new MonthlyFinancialSnapshotService.Source(type,id,type,BigDecimal.valueOf(amount),LocalDate.of(2026,10,5),null,null,null,null,null);}
    private MonthlyFinancialSnapshotService.Bucket bucket(String key,MonthlyFinancialSnapshotService.Source... sources){return new MonthlyFinancialSnapshotService.Bucket(key,key,BigDecimal.ZERO,List.of(sources));}
}
