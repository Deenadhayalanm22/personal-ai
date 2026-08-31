package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpenseCalendarControllerTest {
    private WebAuthenticationService authentication;
    private ExpenseCalendarService calendar;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authentication = mock(WebAuthenticationService.class);
        calendar = mock(ExpenseCalendarService.class);
        WebFinanceController controller = new WebFinanceController(authentication,
                mock(WebLoginRequestService.class), mock(MonthlyExpenseService.class), calendar,
                mock(WebExpenseService.class), mock(WebExpenseTaxonomyService.class), mock(WebTagService.class),
                false, "Lax");
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new WebApiExceptionHandler()).build();
    }

    @Test
    void rejectsMissingMonthWithContractError() throws Exception {
        mvc.perform(get("/api/web/expenses/calendar").cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"))
                .andExpect(jsonPath("$.message").value("month must use YYYY-MM format"));
    }

    @Test
    void rejectsMalformedMonthWithContractError() throws Exception {
        when(authentication.authenticate("token")).thenReturn(user());
        mvc.perform(get("/api/web/expenses/calendar").param("month", "2026-8")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"));
    }

    @Test
    void returnsContractUnauthorizedResponse() throws Exception {
        when(authentication.authenticate(any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/web/expenses/calendar").param("month", "2026-08"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Your session has expired."));
    }

    @Test
    void mapsCalendarFailuresToStableError() throws Exception {
        AppUserEntity user = user();
        when(authentication.authenticate("token")).thenReturn(user);
        when(calendar.calendar(user, java.time.YearMonth.of(2026, 8))).thenThrow(new IllegalStateException());
        mvc.perform(get("/api/web/expenses/calendar").param("month", "2026-08")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CALENDAR_FETCH_FAILED"))
                .andExpect(jsonPath("$.message").value("Unable to load the expense calendar."));
    }

    private AppUserEntity user() {
        AppUserEntity user = new AppUserEntity();
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");
        return user;
    }
}
