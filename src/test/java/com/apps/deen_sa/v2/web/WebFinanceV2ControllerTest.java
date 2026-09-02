package com.apps.deen_sa.v2.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.controller.WebFinanceV2Controller;
import com.apps.deen_sa.v2.service.MonthlyFinancialTransactionService;
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
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        when(authentication.authenticate("session-token")).thenReturn(user);
        when(service.summarize(user, YearMonth.of(2026, 9))).thenReturn(
                new MonthlyFinancialTransactionService.MonthlyExpenseResponse(
                        "2026-09", "INR", new BigDecimal("450"), 2,
                        Map.of("Food & Dining", new BigDecimal("450"))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new WebFinanceV2Controller(authentication, service)).build();

        mvc.perform(get("/api/v2/web/expenses/monthly")
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
}
