package com.apps.deen_sa.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web/enrichment")
public class WebEnrichmentController {
    private final WebAuthenticationService authentication;
    private final DashboardEnrichmentService enrichment;

    public WebEnrichmentController(WebAuthenticationService authentication,
                                   DashboardEnrichmentService enrichment) {
        this.authentication = authentication; this.enrichment = enrichment;
    }

    @GetMapping
    public DashboardEnrichmentService.EnrichmentQueue list(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token) {
        return enrichment.queue(authentication.authenticate(token).getId());
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardTransaction(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token,
            @PathVariable Long id,
            @RequestParam int version) {
        enrichment.discardTransaction(authentication.authenticate(token).getId(), id, version);
    }
}
