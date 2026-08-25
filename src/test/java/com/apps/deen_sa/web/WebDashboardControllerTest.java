package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebDashboardControllerTest {
    @Test
    void summaryEndpointDoesNotLoadOtherDashboardSections() {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        DashboardSummaryService summaries = mock(DashboardSummaryService.class);
        MonthlyExpenseService monthly = mock(MonthlyExpenseService.class);
        DashboardAccountService accounts = mock(DashboardAccountService.class);
        WebExpenseService expenses = mock(WebExpenseService.class);
        DashboardEnrichmentService enrichment = mock(DashboardEnrichmentService.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");
        var expected = new DashboardSummaryService.DashboardSummary(
                "2026-08", "INR", new BigDecimal("900"), 3);
        when(authentication.authenticate("session")).thenReturn(user);
        when(summaries.summarize(user, YearMonth.of(2026, 8))).thenReturn(expected);
        var controller = new WebDashboardController(authentication, summaries, monthly, accounts, expenses, enrichment);

        var result = controller.summary("session", YearMonth.of(2026, 8));

        assertThat(result).isEqualTo(expected);
        verifyNoInteractions(monthly, accounts, expenses, enrichment);
    }
}
