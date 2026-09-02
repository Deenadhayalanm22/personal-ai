package com.apps.deen_sa.v2.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import com.apps.deen_sa.v2.service.FinancialTransactionCalendarService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialTransactionCalendarServiceTest {
    @Test
    void buildsEveryCalendarDayFromFinancialTransactions() {
        FinancialTransactionRepository repository = mock(FinancialTransactionRepository.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        user.setCurrency("INR");
        user.setTimezone("Asia/Kolkata");
        when(repository.summarizeByDay(
                42L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of(
                        new Object[]{LocalDate.of(2026, 9, 3), 2L, new BigDecimal("200.0000")},
                        new Object[]{LocalDate.of(2026, 9, 4), 1L, new BigDecimal("50.0000")}));

        var response = new FinancialTransactionCalendarService(repository)
                .calendar(user, YearMonth.of(2026, 9));

        assertThat(response.days()).hasSize(30);
        assertThat(response.recordedDays()).isEqualTo(2);
        assertThat(response.transactionCount()).isEqualTo(3);
        assertThat(response.totalSpend()).isEqualByComparingTo("250.00");
        assertThat(response.highestSpend()).isEqualByComparingTo("200.00");
        assertThat(response.days().get(2).intensity()).isEqualTo(4);
        assertThat(response.days().get(3).intensity()).isEqualTo(1);
        assertThat(response.days().getFirst().totalSpend().scale()).isEqualTo(2);
    }
}
