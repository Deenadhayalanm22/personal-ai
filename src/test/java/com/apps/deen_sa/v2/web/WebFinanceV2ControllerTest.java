package com.apps.deen_sa.v2.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.controller.WebFinanceV2Controller;
import com.apps.deen_sa.v2.service.MonthlyFinancialTransactionService;
import com.apps.deen_sa.v2.service.FinancialTransactionListService;
import com.apps.deen_sa.v2.service.FinancialTransactionCalendarService;
import com.apps.deen_sa.v2.service.ExpenseEditOptionsService;
import com.apps.deen_sa.v2.service.FinancialTransactionEditService;
import com.apps.deen_sa.web.WebAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebFinanceV2ControllerTest {
    @Test
    void authenticatesSessionAndReturnsV2MonthlyContract() throws Exception {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        MonthlyFinancialTransactionService service = mock(MonthlyFinancialTransactionService.class);
        FinancialTransactionListService listService = mock(FinancialTransactionListService.class);
        FinancialTransactionCalendarService calendar =
                mock(FinancialTransactionCalendarService.class);
        ExpenseEditOptionsService options = mock(ExpenseEditOptionsService.class);
        FinancialTransactionEditService editor = mock(FinancialTransactionEditService.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        when(authentication.authenticate("session-token")).thenReturn(user);
        when(service.summarize(user, YearMonth.of(2026, 9))).thenReturn(
                new MonthlyFinancialTransactionService.MonthlyExpenseResponse(
                        "2026-09", "INR", new BigDecimal("450"), 2,
                        Map.of("Food & Dining", new BigDecimal("450"))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new WebFinanceV2Controller(
                        authentication, service, listService, calendar, options, editor)).build();

        mvc.perform(get("/api/web/expenses/monthly")
                        .param("month", "2026-09")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.total").value(450))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.categories['Food & Dining']").value(450));

        verify(service).summarize(user, YearMonth.of(2026, 9));
    }

    @Test
    void authenticatesSessionAndForwardsV2ExpenseListParameters() throws Exception {
        WebAuthenticationService authentication = mock(WebAuthenticationService.class);
        MonthlyFinancialTransactionService monthly = mock(MonthlyFinancialTransactionService.class);
        FinancialTransactionListService listService = mock(FinancialTransactionListService.class);
        FinancialTransactionCalendarService calendar =
                mock(FinancialTransactionCalendarService.class);
        ExpenseEditOptionsService options = mock(ExpenseEditOptionsService.class);
        FinancialTransactionEditService editor = mock(FinancialTransactionEditService.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        when(authentication.authenticate("session-token")).thenReturn(user);
        when(listService.list(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(YearMonth.of(2026, 9)),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FinancialTransactionListService.ExpensePage(
                        java.util.List.of(), null,
                        new FinancialTransactionListService.FilterSummary(
                                0, new BigDecimal("0.00"), "INR",
                                null, null, java.util.List.of(), "any")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new WebFinanceV2Controller(
                        authentication, monthly, listService, calendar, options, editor)).build();

        mvc.perform(get("/api/web/expenses")
                        .param("month", "2026-09")
                        .param("limit", "5")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "WEB_SESSION", "session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.filterSummary.totalAmount").value(0));

        verify(listService).list(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(YearMonth.of(2026, 9)),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(
                        new FinancialTransactionListService.ExpenseFilter(null, null, null)));
    }
}
