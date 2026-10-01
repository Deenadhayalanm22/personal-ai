package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialActivityServiceTest {
    @Test
    void keepsCompletedCommitmentsAndInvestmentsSeparateFromExpenseRecords() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(42L),
                eq(Date.valueOf("2026-10-01")), eq(Date.valueOf("2026-11-01"))))
                .thenAnswer(call -> {
                    String sql = call.getArgument(0);
                    if (sql.contains("LEFT JOIN recurring_commitment_occurrence")) {
                        assertThat(sql).contains("o.payment_transaction_id = t.id");
                        return List.of(new FinancialActivityService.ActivityItem(
                                "COMMITMENT", 7L, LocalDate.of(2026, 10, 5),
                                new BigDecimal("5000.00"), "Internet bill", "Commitment paid"));
                    }
                    if (sql.contains("investment_transaction")) {
                        assertThat(sql).contains("t.status = 'CONFIRMED'")
                                .contains("t.transaction_kind <> 'OPENING_BALANCE'");
                        return List.of(new FinancialActivityService.ActivityItem(
                                "INVESTMENT", 8L, LocalDate.of(2026, 10, 6),
                                new BigDecimal("2000.00"), "Index fund", "Investment recorded"));
                    }
                    return List.of();
                });
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        user.setCurrency("INR");

        var response = new FinancialActivityService(jdbc).list(user, YearMonth.of(2026, 10));

        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.items()).extracting(FinancialActivityService.ActivityItem::type)
                .containsExactly("INVESTMENT", "COMMITMENT");
        assertThat(response.items()).extracting(FinancialActivityService.ActivityItem::amount)
                .containsExactly(new BigDecimal("2000.00"), new BigDecimal("5000.00"));
        verify(jdbc, times(5)).query(anyString(), any(RowMapper.class), eq(42L),
                eq(Date.valueOf("2026-10-01")), eq(Date.valueOf("2026-11-01")));
    }
}
