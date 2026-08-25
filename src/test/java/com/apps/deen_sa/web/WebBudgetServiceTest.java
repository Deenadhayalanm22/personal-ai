package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.budget.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebBudgetServiceTest {
    @Test
    void createsBudgetForAuthenticatedUser() {
        MonthlyBudgetRepository repository = mock(MonthlyBudgetRepository.class);
        when(repository.findByUserIdAndCategoryIgnoreCase(42L, "Food")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> { MonthlyBudgetEntity value = call.getArgument(0); value.setId(7L); return value; });

        var result = new WebBudgetService(repository).save(42L,
                new WebBudgetService.BudgetUpdate(" Food ", new BigDecimal("12000")));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.category()).isEqualTo("Food");
        assertThat(result.monthlyLimit()).isEqualByComparingTo("12000");
    }
}
