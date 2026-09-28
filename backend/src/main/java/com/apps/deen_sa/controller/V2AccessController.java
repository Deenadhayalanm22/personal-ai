package com.apps.deen_sa.controller;

import com.apps.deen_sa.service.V2AccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** FIN-EPIC-007 — private v2 access and owner-only login-link request. */
@RestController
@RequestMapping("/api/web/v2")
public class V2AccessController {
    private final V2AccessService access;

    public V2AccessController(V2AccessService access) { this.access = access; }

    @PostMapping("/login-link")
    public ResponseEntity<LoginLinkResponse> requestLoginLink(@RequestBody LoginLinkRequest request,
            HttpServletRequest httpRequest) {
        access.requestLink(request.phoneNumber(), httpRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(new LoginLinkResponse(
                "If this number can use the private preview, we sent a sign-in link to its WhatsApp account."));
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(@CookieValue(name = "WEB_SESSION", required = false) String token) {
        access.requireOwner(token);
        return ResponseEntity.noContent().build();
    }

    public record LoginLinkRequest(String phoneNumber) { }
    public record LoginLinkResponse(String message) { }
}
