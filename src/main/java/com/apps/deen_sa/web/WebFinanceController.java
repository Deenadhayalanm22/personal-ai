package com.apps.deen_sa.web;

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
    private final WebExpenseTaxonomyService taxonomy;
    private final WebUserReferenceEntityTypeService referenceEntityTypes;
    private final WebUserReferencePreferenceService referencePreferences;
    private final boolean secureCookies;
    private final String cookieSameSite;

    public WebFinanceController(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
            WebExpenseTaxonomyService taxonomy,
            WebUserReferenceEntityTypeService referenceEntityTypes,
            WebUserReferencePreferenceService referencePreferences,
            @Value("${app.web.secure-cookies:false}") boolean secureCookies,
            @Value("${app.web.cookie-same-site:Lax}") String cookieSameSite) {
        this.authentication = authentication; this.loginRequests = loginRequests;
        this.taxonomy = taxonomy;
        this.referenceEntityTypes = referenceEntityTypes;
        this.referencePreferences = referencePreferences;
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


    @GetMapping("/expense-taxonomy")
    public WebExpenseTaxonomyService.TaxonomyResponse expenseTaxonomy(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        authentication.authenticate(token);
        return taxonomy.options();
    }

    @GetMapping("/reference-entity-types")
    public WebUserReferenceEntityTypeService.UserReferenceEntityTypesResponse referenceEntityTypes(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        authentication.authenticate(token);
        return referenceEntityTypes.options();
    }

    @PostMapping("/reference-preferences")
    @ResponseStatus(HttpStatus.CREATED)
    public WebUserReferencePreferenceService.UserReferencePreferenceResponse createReferencePreference(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebUserReferencePreferenceService.UserReferencePreferenceRequest request) {
        return referencePreferences.create(authentication.authenticate(token), request);
    }

    @GetMapping("/reference-preferences")
    public WebUserReferencePreferenceService.UserReferencePreferenceListResponse referencePreferences(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return referencePreferences.list(authentication.authenticate(token));
    }

    private String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    public record LoginLinkRequest(String phoneNumber) { }
    public record LoginLinkResponse(String message) { }
    public record MagicLinkRequest(String token) { }
    public record AuthResponse(boolean authenticated, Instant expiresAt) { }
}
