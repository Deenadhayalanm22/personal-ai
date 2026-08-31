package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExpenseCalendarServiceTest {
    @Test
    void returnsEveryLocalDateWithCountsTotalsAndMonthMaxIntensity() {
        StateChangeRepository repository = mock(StateChangeRepository.class);
        AppUserEntity user = user();
        Instant start = Instant.parse("2026-07-31T18:30:00Z");
        Instant end = Instant.parse("2026-08-31T18:30:00Z");
        when(repository.summarizeExpensesByLocalDay("42", start, end, "Asia/Kolkata")).thenReturn(List.of(
                new Object[]{"2026-08-01", 2L, new BigDecimal("1000.00")},
                new Object[]{"2026-08-03", 4L, new BigDecimal("4000.00")},
                new Object[]{"2026-08-04", 1L, new BigDecimal("2500.00")}));

        var result = new ExpenseCalendarService(repository).calendar(user, YearMonth.of(2026, 8));

        assertThat(result.month()).isEqualTo("2026-08");
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(result.days()).hasSize(31);
        assertThat(result.recordedDays()).isEqualTo(3);
        assertThat(result.transactionCount()).isEqualTo(7);
        assertThat(result.totalSpend()).isEqualByComparingTo("7500.00");
        assertThat(result.highestSpend()).isEqualByComparingTo("4000.00");
        assertThat(result.intensityMethod()).isEqualTo("month-max-v1");
        assertThat(result.days().get(0).intensity()).isEqualTo(1);
        assertThat(result.days().get(1).totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.days().get(1).intensity()).isZero();
        assertThat(result.days().get(2).intensity()).isEqualTo(4);
        assertThat(result.days().get(3).intensity()).isEqualTo(3);
    }

    @Test
    void returnsACompleteZeroFilledEmptyMonth() {
        StateChangeRepository repository = mock(StateChangeRepository.class);
        when(repository.summarizeExpensesByLocalDay(anyString(), any(), any(), anyString()))
                .thenReturn(List.of());

        var result = new ExpenseCalendarService(repository).calendar(user(), YearMonth.of(2026, 2));

        assertThat(result.days()).hasSize(28);
        assertThat(result.recordedDays()).isZero();
        assertThat(result.transactionCount()).isZero();
        assertThat(result.totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.highestSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.days()).allSatisfy(day -> {
            assertThat(day.transactionCount()).isZero();
            assertThat(day.totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(day.intensity()).isZero();
        });
    }

    @Test
    void appliesExactIntensityBoundaries() {
        BigDecimal highest = new BigDecimal("100");
        assertThat(ExpenseCalendarService.intensity(BigDecimal.ZERO, highest)).isZero();
        assertThat(ExpenseCalendarService.intensity(new BigDecimal("25"), highest)).isEqualTo(1);
        assertThat(ExpenseCalendarService.intensity(new BigDecimal("25.01"), highest)).isEqualTo(2);
        assertThat(ExpenseCalendarService.intensity(new BigDecimal("50"), highest)).isEqualTo(2);
        assertThat(ExpenseCalendarService.intensity(new BigDecimal("50.01"), highest)).isEqualTo(3);
        assertThat(ExpenseCalendarService.intensity(new BigDecimal("75"), highest)).isEqualTo(3);
        assertThat(ExpenseCalendarService.intensity(new BigDecimal("75.01"), highest)).isEqualTo(4);
    }

    private AppUserEntity user() {
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        user.setTimezone("Asia/Kolkata");
        user.setCurrency("INR");
        return user;
    }
}
