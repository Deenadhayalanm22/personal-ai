package com.apps.deen_sa.controller;

import com.apps.deen_sa.insights.WebExpenseCaptureService;
import com.apps.deen_sa.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** FIN-EPIC-001, FIN-EPIC-004: free manual preparation scoped to the active profile. */
@RestController
@RequiredArgsConstructor
public class ManualExpenseCaptureController {
    private final WebAuthenticationService authentication;
    private final ManualExpenseCaptureService capture;
    @PostMapping("/api/web/expenses/manual")
    public ResponseEntity<WebExpenseCaptureService.Response> prepare(
            @CookieValue(name="WEB_SESSION", required=false) String token,
            @RequestBody ManualExpenseCaptureService.Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(capture.prepare(authentication.authenticate(token), request));
    }
}
