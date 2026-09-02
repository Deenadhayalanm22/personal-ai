package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.conversation.context.PendingActionContextService;
import com.apps.deen_sa.finance.expense.correction.WhatsAppExpenseEditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpenseCalendarControllerTest {
    private WebAuthenticationService authentication;
    private ExpenseCalendarService calendar;
    private PendingActionContextService actionContexts;
    private WebExpenseService expenses;
    private WhatsAppExpenseEditService whatsappExpenseEdits;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authentication = mock(WebAuthenticationService.class);
        calendar = mock(ExpenseCalendarService.class);
        actionContexts = mock(PendingActionContextService.class);
        expenses = mock(WebExpenseService.class);
        whatsappExpenseEdits = mock(WhatsAppExpenseEditService.class);
        WebFinanceController controller = new WebFinanceController(authentication,
                mock(WebLoginRequestService.class), mock(MonthlyExpenseService.class), calendar,
                actionContexts,
                whatsappExpenseEdits,
                expenses, mock(WebExpenseTaxonomyService.class), mock(WebTagService.class),
                false, "Lax");
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new WebApiExceptionHandler()).build();
    }

    @Test
    void rejectsMissingMonthWithContractError() throws Exception {
        mvc.perform(get("/api/web/old/expenses/calendar").cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"))
                .andExpect(jsonPath("$.message").value("month must use YYYY-MM format"));
    }

    @Test
    void rejectsMalformedMonthWithContractError() throws Exception {
        when(authentication.authenticate("token")).thenReturn(user());
        mvc.perform(get("/api/web/old/expenses/calendar").param("month", "2026-8")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"));
    }

    @Test
    void returnsContractUnauthorizedResponse() throws Exception {
        when(authentication.authenticate(any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/web/old/expenses/calendar").param("month", "2026-08"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Your session has expired."));
    }

    @Test
    void mapsCalendarFailuresToStableError() throws Exception {
        AppUserEntity user = user();
        when(authentication.authenticate("token")).thenReturn(user);
        when(calendar.calendar(user, java.time.YearMonth.of(2026, 8))).thenThrow(new IllegalStateException());
        mvc.perform(get("/api/web/old/expenses/calendar").param("month", "2026-08")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CALENDAR_FETCH_FAILED"))
                .andExpect(jsonPath("$.message").value("Unable to load the expense calendar."));
    }

    @Test
    void createsMissingTransactionContext() throws Exception {
        AppUserEntity user = user();
        when(authentication.authenticate("token")).thenReturn(user);
        var request = new PendingActionContextService.ContextRequest(
                PendingActionContextService.TYPE, "2026-08-14", "Asia/Kolkata");
        when(actionContexts.create(eq(42L), any())).thenReturn(
                new PendingActionContextService.ContextResponse("ctx_123", "ACTIVE",
                        "2026-08-14", java.time.Instant.parse("2026-08-31T14:45:00Z"),
                        "https://wa.me/919999999999"));
        mvc.perform(post("/api/web/old/expenses/calendar/context")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MISSING_TRANSACTION_DATE","date":"2026-08-14","timezone":"Asia/Kolkata"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contextId").value("ctx_123"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.date").value("2026-08-14"))
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/919999999999"));
        verify(actionContexts).create(eq(42L), eq(request));
    }

    @Test
    void forwardsSelectedDateToExpenseFilter() throws Exception {
        AppUserEntity user = user();
        when(authentication.authenticate("token")).thenReturn(user);

        mvc.perform(get("/api/web/old/expenses")
                        .param("month", "2026-08")
                        .param("date", "2026-08-27")
                        .param("limit", "50")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isOk());

        ArgumentCaptor<WebExpenseService.ExpenseFilter> filter =
                ArgumentCaptor.forClass(WebExpenseService.ExpenseFilter.class);
        verify(expenses).list(eq(user), eq(java.time.YearMonth.of(2026, 8)), eq(50), isNull(), filter.capture());
        org.assertj.core.api.Assertions.assertThat(filter.getValue().date())
                .isEqualTo(java.time.LocalDate.of(2026, 8, 27));
    }

    @Test
    void deletesExpenseWithoutVersionParameter() throws Exception {
        AppUserEntity user = user();
        when(authentication.authenticate("token")).thenReturn(user);

        mvc.perform(delete("/api/web/old/expenses/88")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token")))
                .andExpect(status().isNoContent());

        verify(expenses).delete(user, 88L);
    }

    @Test
    void updatesExpenseAmountAndDate() throws Exception {
        AppUserEntity user = user();
        when(authentication.authenticate("token")).thenReturn(user);

        mvc.perform(patch("/api/web/old/expenses/87")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":171,"transactionDate":"2026-08-27","version":1}
                                """))
                .andExpect(status().isOk());

        verify(expenses).edit(user, 87L,
                new WebExpenseService.ExpenseUpdate(new java.math.BigDecimal("171"),
                        java.time.LocalDate.of(2026, 8, 27)));
    }

    @Test
    void createsWhatsAppExpenseEditContextWithoutVersion() throws Exception {
        AppUserEntity user = user();
        when(authentication.authenticate("token")).thenReturn(user);
        when(whatsappExpenseEdits.create(42L, 95L, "EDIT_TRANSACTION")).thenReturn(
                new PendingActionContextService.ContextResponse("ctx_edit", "ACTIVE", null,
                        java.time.Instant.parse("2026-09-01T06:00:00Z"), "https://wa.me/919999999999"));

        mvc.perform(post("/api/web/expenses/95/whatsapp-context")
                        .cookie(new jakarta.servlet.http.Cookie("WEB_SESSION", "token"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EDIT_TRANSACTION\",\"version\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contextId").value("ctx_edit"));

        verify(whatsappExpenseEdits).create(42L, 95L, "EDIT_TRANSACTION");
    }

    private AppUserEntity user() {
        AppUserEntity user = new AppUserEntity();
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");
        return user;
    }
}
