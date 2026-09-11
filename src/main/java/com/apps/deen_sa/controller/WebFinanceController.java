package com.apps.deen_sa.controller;

import com.apps.deen_sa.service.PendingActionContextService;
import com.apps.deen_sa.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/web")
public class WebFinanceController {
    static final String SESSION_COOKIE = "WEB_SESSION";
    private final WebManager webManager;
    private final boolean secureCookies;
    private final String cookieSameSite;

    public WebFinanceController(
            WebManager webManager,
            @Value("${app.web.secure-cookies:false}") boolean secureCookies,
            @Value("${app.web.cookie-same-site:Lax}") String cookieSameSite) {
        this.webManager = webManager;
        this.secureCookies = secureCookies;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/auth/login-link")
    public ResponseEntity<WebManager.LoginLinkResponse> requestLoginLink(
            @RequestBody WebManager.LoginLinkRequest request, HttpServletRequest httpRequest) {
        webManager.requestLoginLink(request.phoneNumber(), httpRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(new WebManager.LoginLinkResponse(
                "If this number is registered, we sent a login link to its WhatsApp account."));
    }

    @PostMapping("/auth/magic-link")
    public ResponseEntity<WebManager.AuthResponse> exchange(
            @RequestBody WebManager.MagicLinkRequest request, HttpServletResponse response) {
        WebAuthenticationService.SessionGrant grant = webManager.exchange(request.token());
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookie(grant.token(), Duration.between(Instant.now(), grant.expiresAt())).toString());
        return ResponseEntity.ok(new WebManager.AuthResponse(true, grant.expiresAt()));
    }

    @GetMapping("/auth/session")
    public WebManager.AuthResponse session(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        webManager.validateSession(token);
        return new WebManager.AuthResponse(true, null);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                       HttpServletResponse response) {
        webManager.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    @GetMapping("/expense-taxonomy")
    public WebExpenseTaxonomyService.TaxonomyResponse expenseTaxonomy(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.expenseTaxonomy(token);
    }

    @GetMapping("/reference-entity-types")
    public WebManager.UserReferenceEntityTypesResponse referenceEntityTypes(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.referenceEntityTypes(token);
    }

    @PostMapping("/reference-preferences")
    @ResponseStatus(HttpStatus.CREATED)
    public WebUserReferencePreferenceService.UserReferencePreferenceResponse createReferencePreference(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebUserReferencePreferenceService.UserReferencePreferenceRequest request) {
        return webManager.createReferencePreference(token, request);
    }

    @GetMapping("/reference-preferences")
    public WebUserReferencePreferenceService.UserReferencePreferenceListResponse referencePreferences(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.referencePreferences(token);
    }

    @GetMapping("/expenses/monthly")
    public MoneyStoriesService.MonthlyStoriesApiResponse monthlyExpenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        return webManager.monthlyExpenses(token, month);
    }

    @GetMapping("/expenses")
    public FinancialTransactionListService.ExpensePage expenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory) {
        return webManager.expenses(token, month, date, limit, beforeId, category, subcategory);
    }

    @GetMapping("/expenses/calendar")
    public FinancialTransactionCalendarService.CalendarResponse expenseCalendar(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam String month) {
        return webManager.expenseCalendar(token, month);
    }

    @GetMapping("/expenses/options")
    public ExpenseEditOptionsService.ExpenseEditOptions expenseEditOptions(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.expenseEditOptions(token);
    }

    @PatchMapping("/expenses/{id}")
    public FinancialTransactionListService.ExpenseItem editExpense(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id, @RequestBody FinancialTransactionEditService.ExpenseUpdate request) {
        return webManager.editExpense(token, id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                              @PathVariable Long id) {
        webManager.deleteExpense(token, id);
    }

    @PostMapping("/expenses/calendar/context")
    @ResponseStatus(HttpStatus.CREATED)
    public PendingActionContextService.ContextResponse createPendingActionContext(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody PendingActionContextService.ContextRequest request) {
        return webManager.createPendingActionContext(token, request);
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite)
                .path("/api/web").maxAge(maxAge).build();
    }
}
