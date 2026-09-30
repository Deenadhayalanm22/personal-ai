package com.apps.deen_sa.controller;

import com.apps.deen_sa.credits.CreditStore;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.service.WebAuthenticationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import static com.apps.deen_sa.credits.CreditPolicy.error;

/** FIN-EPIC-003 and FIN-EPIC-004: active-profile balances and role-based super-admin credit management. */
@RestController
@RequestMapping("/api/web/ai-credits")
public class AiCreditController {
    private final WebAuthenticationService authentication;
    private final CreditStore credits;
    public AiCreditController(WebAuthenticationService authentication, CreditStore credits) {
        this.authentication = authentication; this.credits = credits;
    }
    @GetMapping
    public ResponseEntity<CreditStore.Balance> balance(@CookieValue(name="WEB_SESSION", required=false) String token) {
        return ok(credits.balance(authentication.authenticate(token).getId()));
    }
    @GetMapping("/permissions")
    public ResponseEntity<?> permissions(@CookieValue(name="WEB_SESSION", required=false) String token) {
        return ok(Map.of("admin", "SUPER_ADMIN".equals(authentication.authenticate(token).getRole())));
    }
    @GetMapping("/ledger")
    public ResponseEntity<?> ledger(@CookieValue(name="WEB_SESSION", required=false) String token) {
        return ok(credits.ledger(authentication.authenticate(token).getId()));
    }
    @GetMapping("/admin/users")
    public ResponseEntity<?> users(@CookieValue(name="WEB_SESSION", required=false) String token,
                                   @RequestParam(defaultValue="") String search) {
        admin(token); return ok(credits.users(search));
    }
    @PostMapping("/admin/users/{userId}/grants")
    public ResponseEntity<?> grant(@CookieValue(name="WEB_SESSION", required=false) String token,
                                   @PathVariable long userId, @RequestBody Grant input) {
        var actor = admin(token);
        return ok(credits.grant(userId,input.requestId(),input.amount(),actor.getId(),input.note()));
    }
    @PutMapping("/admin/users/{userId}/access")
    public ResponseEntity<?> access(@CookieValue(name="WEB_SESSION", required=false) String token,
                                    @PathVariable long userId, @RequestBody Access input) {
        var actor = admin(token);
        if (input.paused()==null) throw error(HttpStatus.BAD_REQUEST,"INVALID_CREDIT_ACCESS","Specify whether access is paused.");
        return ok(credits.pause(userId,input.paused(),actor.getId()));
    }
    @GetMapping("/admin/users/{userId}/ledger")
    public ResponseEntity<?> userLedger(@CookieValue(name="WEB_SESSION", required=false) String token, @PathVariable long userId) {
        admin(token); return ok(credits.ledger(userId));
    }
    @GetMapping("/admin/pending")
    public ResponseEntity<?> pending(@CookieValue(name="WEB_SESSION", required=false) String token) {
        admin(token); return ok(credits.pendingCalls());
    }
    @PostMapping("/admin/pending/{id}/resolve")
    public ResponseEntity<?> resolve(@CookieValue(name="WEB_SESSION", required=false) String token,
                                    @PathVariable UUID id, @RequestBody Resolution input) {
        var actor = admin(token);
        credits.reconcile(id,input.amount(),actor.getId(),input.note());
        return ok(Map.of("resolved",true));
    }
    private AppUserEntity admin(String token) {
        AppUserEntity user = authentication.authenticate(token);
        if (!"SUPER_ADMIN".equals(user.getRole()))
            throw error(HttpStatus.FORBIDDEN,"ADMIN_REQUIRED","Super-admin access is required to manage AI credits.");
        return user;
    }
    private static <T> ResponseEntity<T> ok(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    public record Grant(UUID requestId, BigDecimal amount, String note) {}
    public record Access(Boolean paused) {}
    public record Resolution(BigDecimal amount,String note) {}
}
