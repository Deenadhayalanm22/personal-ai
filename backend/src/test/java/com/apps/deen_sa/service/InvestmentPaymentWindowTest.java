package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class InvestmentPaymentWindowTest {
 @Test void sipAndStockSkipCheckWindowEvenForStaleDueStatus() {
  for (InvestmentAssetType type : List.of(InvestmentAssetType.STOCK, InvestmentAssetType.MUTUAL_FUND)) {
   for (String date : List.of("2026-09-28", "2026-10-01")) {
    AppUserEntity user = new AppUserEntity(); user.setId(1L);
    UserInvestmentEntity investment = new UserInvestmentEntity();
    investment.setId(2L); investment.setUser(user); investment.setAssetType(type); investment.setSipDay(5);
    InvestmentTransactionEntity tx = new InvestmentTransactionEntity();
    tx.setInvestment(investment); tx.setScheduledMonth(LocalDate.of(2026,10,1));
    tx.setStatus(date.equals("2026-10-01") ? InvestmentTransactionStatus.SCHEDULED : InvestmentTransactionStatus.DUE);
    var investments=mock(UserInvestmentRepository.class);
    var transactions=mock(InvestmentTransactionRepository.class);
    when(investments.findByIdAndUserId(2L,1L)).thenReturn(Optional.of(investment));
    when(transactions.findByInvestmentIdAndTransactionKindAndScheduledMonth(2L,InvestmentTransactionKind.SIP,tx.getScheduledMonth())).thenReturn(Optional.of(tx));
    when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));
    Clock clock=Clock.fixed(Instant.parse(date+"T09:00:00Z"),ZoneId.of("Asia/Kolkata"));
    Runnable skip = type==InvestmentAssetType.STOCK
      ? () -> new WebStockService(investments,transactions,null,clock).skipMonthlyPlan(user,2L,YearMonth.of(2026,10))
      : () -> new WebMutualFundService(investments,transactions,null,clock).skipSip(user,2L,YearMonth.of(2026,10));
    if(date.equals("2026-10-01")) {
     skip.run(); assertEquals(InvestmentTransactionStatus.SKIPPED,tx.getStatus());
     assertThrows(WebApiException.class,skip::run);
    } else {
     assertThrows(WebApiException.class,skip::run); verify(transactions,never()).save(any());
    }
   }
  }
 }
}
