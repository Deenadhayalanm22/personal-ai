package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import jakarta.servlet.http.HttpServletRequest;
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
    private final WebLoginRequestService loginRequests;
    private final MonthlyExpenseService monthlyExpenses;
    private final WebExpenseService expenses;
    private final WebExpenseTaxonomyService taxonomy;
    private final boolean secureCookies;
    private final String cookieSameSite;

    public WebFinanceController(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
            MonthlyExpenseService monthlyExpenses,
            WebExpenseService expenses, WebExpenseTaxonomyService taxonomy,
            @Value("${app.web.secure-cookies:false}") boolean secureCookies,
            @Value("${app.web.cookie-same-site:Lax}") String cookieSameSite) {
        this.authentication = authentication; this.loginRequests = loginRequests;
        this.monthlyExpenses = monthlyExpenses;
        this.expenses = expenses;
        this.taxonomy = taxonomy;
        this.secureCookies = secureCookies; this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/auth/login-link")
    public ResponseEntity<LoginLinkResponse> requestLoginLink(@RequestBody LoginLinkRequest request,
                                                               HttpServletRequest httpRequest) {
        loginRequests.request(request.phoneNumber(), clientAddress(httpRequest));
        return ResponseEntity.accepted().body(new LoginLinkResponse(
                "If this number is registered, we sent a login link to its WhatsApp account."));
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

    @GetMapping("/auth/session")
    public AuthResponse session(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        authentication.authenticate(token);
        return new AuthResponse(true, null);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                       HttpServletResponse response) {
        authentication.logout(token);
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite).path("/api/web")
                .maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @GetMapping("/expenses/monthly")
    public MonthlyExpenseService.MonthlyExpenseResponse monthlyExpenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = selectedMonth(user, month);
        return monthlyExpenses.summarize(user, selected);
    }

    @GetMapping("/expenses")
    public WebExpenseService.ExpensePage expenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory) {
        AppUserEntity user = authentication.authenticate(token);
        return expenses.list(user, selectedMonth(user, month), limit, beforeId,
                new WebExpenseService.ExpenseFilter(accountId, category, subcategory));
    }

    @GetMapping("/expense-taxonomy")
    public WebExpenseTaxonomyService.TaxonomyResponse expenseTaxonomy(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        authentication.authenticate(token);
        return taxonomy.options();
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

    private String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    public record LoginLinkRequest(String phoneNumber) { }
    public record LoginLinkResponse(String message) { }
    public record MagicLinkRequest(String token) { }
    public record AuthResponse(boolean authenticated, Instant expiresAt) { }
}
