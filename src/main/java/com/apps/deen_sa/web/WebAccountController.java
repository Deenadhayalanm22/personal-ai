package com.apps.deen_sa.web;

import org.springframework.web.bind.annotation.*;
import com.apps.deen_sa.conversation.AppUserEntity;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/web/accounts")
public class WebAccountController {
    private final WebAuthenticationService authentication;
    private final WebAccountEnrichmentService enrichment;
    private final DashboardAccountService accounts;

    public WebAccountController(WebAuthenticationService authentication, WebAccountEnrichmentService enrichment,
                                DashboardAccountService accounts) {
        this.authentication = authentication; this.enrichment = enrichment; this.accounts = accounts;
    }

    @GetMapping
    public List<DashboardAccountService.DashboardAccount> list(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token) {
        AppUserEntity user = authentication.authenticate(token);
        return accounts.activeAccounts(user.getId(), ZoneId.of(user.getTimezone()));
    }

    @PatchMapping("/{id}/enrichment")
    public DashboardAccountService.DashboardAccount enrich(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token,
            @PathVariable Long id,
            @RequestBody WebAccountEnrichmentService.AccountEnrichment request) {
        return enrichment.enrich(authentication.authenticate(token).getId(), id, request);
    }
}
