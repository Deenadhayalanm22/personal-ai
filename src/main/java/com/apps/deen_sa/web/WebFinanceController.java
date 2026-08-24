package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/web")
public class WebFinanceController {
    static final String SESSION_COOKIE = "WEB_SESSION";
    private final WebAuthenticationService authentication;
    private final MonthlyExpenseService monthlyExpenses;
    private final WebExpenseService expenses;
    private final boolean secureCookies;
    private final String cookieSameSite;

    public WebFinanceController(WebAuthenticationService authentication, MonthlyExpenseService monthlyExpenses,
            WebExpenseService expenses,
            @Value("${app.web.secure-cookies:false}") boolean secureCookies,
            @Value("${app.web.cookie-same-site:Lax}") String cookieSameSite) {
        this.authentication = authentication; this.monthlyExpenses = monthlyExpenses;
        this.expenses = expenses;
        this.secureCookies = secureCookies; this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/auth/magic-link")
    public ResponseEntity<AuthResponse> exchange(@RequestBody MagicLinkRequest request,
                                                  HttpServletResponse response) {
        WebAuthenticationService.SessionGrant grant = authentication.exchange(request.token());
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, grant.token())
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite).path("/api/web")
                .maxAge(Duration.between(Instant.now(), grant.expiresAt())).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(new AuthResponse(true, grant.expiresAt()));
    }

    @GetMapping("/expenses/monthly")
    public MonthlyExpenseService.MonthlyExpenseResponse monthlyExpenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = selectedMonth(user, month);
        return monthlyExpenses.summarize(user, selected);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = selectedMonth(user, month);
        return new DashboardResponse(monthlyExpenses.summarize(user, selected),
                expenses.list(user, selected, 10, null));
    }

    @GetMapping("/expenses")
    public WebExpenseService.ExpensePage expenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId) {
        AppUserEntity user = authentication.authenticate(token);
        return expenses.list(user, selectedMonth(user, month), limit, beforeId);
    }

    @PatchMapping("/expenses/{id}/classification")
    public WebExpenseService.ExpenseItem editClassification(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id,
            @RequestBody WebExpenseService.ClassificationUpdate request) {
        return expenses.editClassification(authentication.authenticate(token), id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id,
            @RequestParam int version) {
        expenses.delete(authentication.authenticate(token), id, version);
    }

    private YearMonth selectedMonth(AppUserEntity user, YearMonth requested) {
        return requested == null ? YearMonth.now(ZoneId.of(user.getTimezone())) : requested;
    }

    public record MagicLinkRequest(String token) { }
    public record AuthResponse(boolean authenticated, Instant expiresAt) { }
    public record DashboardResponse(MonthlyExpenseService.MonthlyExpenseResponse summary,
                                    WebExpenseService.ExpensePage recentExpenses) { }
}
