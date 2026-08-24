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
    private final boolean secureCookies;
    private final String cookieSameSite;

    public WebFinanceController(WebAuthenticationService authentication, MonthlyExpenseService monthlyExpenses,
            @Value("${app.web.secure-cookies:false}") boolean secureCookies,
            @Value("${app.web.cookie-same-site:Lax}") String cookieSameSite) {
        this.authentication = authentication; this.monthlyExpenses = monthlyExpenses;
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
        YearMonth selected = month == null ? YearMonth.now(ZoneId.of(user.getTimezone())) : month;
        return monthlyExpenses.summarize(user, selected);
    }

    public record MagicLinkRequest(String token) { }
    public record AuthResponse(boolean authenticated, Instant expiresAt) { }
}
