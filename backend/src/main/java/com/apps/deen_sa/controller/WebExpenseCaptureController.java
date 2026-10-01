package com.apps.deen_sa.controller;

import com.apps.deen_sa.insights.WebExpenseCaptureService;
import com.apps.deen_sa.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** FIN-EPIC-001, FIN-EPIC-003, FIN-EPIC-004: active-profile web drafts and explicit confirmation. */
@RestController
@RequestMapping("/api/web/expense-chat/capture")
public class WebExpenseCaptureController {
    private final WebAuthenticationService authentication;
    private final WebExpenseCaptureService capture;
    private final ExpenseConfirmationCommandHandler confirmation;
    public WebExpenseCaptureController(WebAuthenticationService authentication, WebExpenseCaptureService capture,
                                       ExpenseConfirmationCommandHandler confirmation) {
        this.authentication=authentication;this.capture=capture;this.confirmation=confirmation;
    }
    @PostMapping
    public ResponseEntity<WebExpenseCaptureService.Response> capture(
            @CookieValue(name="WEB_SESSION",required=false) String token,
            @RequestBody WebExpenseCaptureService.Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(capture.capture(authentication.authenticate(token),request));
    }
    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@CookieValue(name="WEB_SESSION",required=false) String token,@PathVariable Long id) {
        var date=confirmation.handleWeb(authentication.authenticate(token).getId(),id,true);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","RECORDED","date",date.toString()));
    }
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@CookieValue(name="WEB_SESSION",required=false) String token,@PathVariable Long id) {
        confirmation.handleWeb(authentication.authenticate(token).getId(),id,false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","CANCELLED"));
    }
}
