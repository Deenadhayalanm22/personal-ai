package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardSummaryServiceTest {
    @Test
    void loadsHeadlineMetricsWithOneAggregateRepositoryCall() {
        StateChangeRepository expenses = mock(StateChangeRepository.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");
        when(expenses.summarizeActiveExpensesForPeriod(eq("42"), any(), any()))
                .thenReturn(Collections.singletonList(new Object[]{12L, new BigDecimal("4500")}));

        var result = new DashboardSummaryService(expenses).summarize(user, YearMonth.of(2026, 8));

        assertThat(result.month()).isEqualTo("2026-08");
        assertThat(result.totalSpend()).isEqualByComparingTo("4500");
        assertThat(result.transactionCount()).isEqualTo(12);
        verify(expenses, times(1)).summarizeActiveExpensesForPeriod(eq("42"), any(), any());
        verifyNoMoreInteractions(expenses);
    }
}
