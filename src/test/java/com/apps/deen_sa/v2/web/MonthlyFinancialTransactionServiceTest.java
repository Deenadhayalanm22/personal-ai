package com.apps.deen_sa.v2.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import com.apps.deen_sa.v2.service.MonthlyFinancialTransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyFinancialTransactionServiceTest {
    @Test
    void summarizesNonDeletedFinancialTransactionsForMonth() {
        FinancialTransactionRepository repository = mock(FinancialTransactionRepository.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        user.setCurrency("INR");
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 10, 1);
        when(repository.sumByCategoryForPeriod(42L, start, end)).thenReturn(List.of(
                new Object[]{"Food & Dining", new BigDecimal("450.00")},
                new Object[]{"Travel", new BigDecimal("300.00")}));
        when(repository
                .countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanAndDeletedAtIsNull(
                        42L, start, end))
                .thenReturn(3L);

        var response = new MonthlyFinancialTransactionService(repository)
                .summarize(user, YearMonth.of(2026, 9));

        assertThat(response.month()).isEqualTo("2026-09");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.total()).isEqualByComparingTo("750.00");
        assertThat(response.transactionCount()).isEqualTo(3);
        assertThat(response.categories())
                .containsEntry("Food & Dining", new BigDecimal("450.00"))
                .containsEntry("Travel", new BigDecimal("300.00"));
    }
}
