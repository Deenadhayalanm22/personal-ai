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
    @Test void separatesBillsFromInvestmentsAndSavingsAndRemovesConfirmedOrSkippedPlans() {
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
            bucket("PLANNED_INVESTING",source("MUTUAL_FUND_SIP","2",1000),source("STOCK_MONTHLY_PLAN","3",2000),source("MUTUAL_FUND_SIP","4",400)),
            bucket("COMMITMENT_SAVINGS",source("COMMITMENT_SAVINGS","5",200)))));
        var service=new MonthlyPaymentOverviewService(snapshots,occurrences,loans,investments,Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC));
        var result=service.forMonth(u,month);
        assertThat(result.stillToPay()).isEqualByComparingTo("300");assertThat(result.plannedInvesting()).isEqualByComparingTo("400");assertThat(result.plannedSavings()).isEqualByComparingTo("200");
        assertThat(service.forMonth(u,month.minusMonths(1))).isNull();
    }
    private MonthlyFinancialSnapshotService.Source source(String type,String id,int amount){return new MonthlyFinancialSnapshotService.Source(type,id,type,BigDecimal.valueOf(amount),LocalDate.of(2026,10,5),null,null,null,null,null);}
    private MonthlyFinancialSnapshotService.Bucket bucket(String key,MonthlyFinancialSnapshotService.Source... sources){return new MonthlyFinancialSnapshotService.Bucket(key,key,BigDecimal.ZERO,List.of(sources));}
}
