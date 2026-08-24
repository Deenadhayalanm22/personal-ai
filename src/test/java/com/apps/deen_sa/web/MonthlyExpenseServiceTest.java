package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MonthlyExpenseServiceTest {
    @Test
    void returnsCategoryTotalsForTheUsersCalendarMonth() {
        StateChangeRepository expenses = mock(StateChangeRepository.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");
        Instant start = Instant.parse("2026-07-31T18:30:00Z");
        Instant end = Instant.parse("2026-08-31T18:30:00Z");
        when(expenses.sumExpensesByCategoryForPeriod("42", start, end)).thenReturn(List.of(
                new Object[]{"Groceries", new BigDecimal("1200.00")},
                new Object[]{"Travel", new BigDecimal("300.00")}));
        when(expenses.countExpensesForPeriod("42", start, end)).thenReturn(7L);

        var result = new MonthlyExpenseService(expenses).summarize(user, YearMonth.of(2026, 8));

        assertThat(result.month()).isEqualTo("2026-08");
        assertThat(result.total()).isEqualByComparingTo("1500.00");
        assertThat(result.transactionCount()).isEqualTo(7);
        assertThat(result.categories()).containsEntry("Groceries", new BigDecimal("1200.00"));
    }
}
