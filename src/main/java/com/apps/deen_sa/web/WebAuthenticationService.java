package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;

@Service
public class WebAuthenticationService {
    private final MagicLinkRepository magicLinks;
    private final WebSessionRepository sessions;
    private final AppUserRepository users;
    private final Duration sessionExpiry;
    private final SecureRandom random;
    private final Clock clock;

    @Autowired
    public WebAuthenticationService(MagicLinkRepository magicLinks, WebSessionRepository sessions,
            AppUserRepository users, @Value("${app.web.session-expiry:12h}") String sessionExpiry) {
        this(magicLinks, sessions, users, DurationStyle.detectAndParse(sessionExpiry),
                new SecureRandom(), Clock.systemUTC());
    }

    WebAuthenticationService(MagicLinkRepository magicLinks, WebSessionRepository sessions,
            AppUserRepository users, Duration sessionExpiry, SecureRandom random, Clock clock) {
        this.magicLinks = magicLinks; this.sessions = sessions; this.users = users;
        this.sessionExpiry = sessionExpiry; this.random = random; this.clock = clock;
    }

    @Transactional
    public SessionGrant exchange(String token) {
        if (token == null || token.isBlank()) throw unauthorized();
        Instant now = clock.instant();
        MagicLinkEntity link = magicLinks.findByTokenHash(MagicLinkService.hash(token))
                .orElseThrow(WebAuthenticationService::unauthorized);
        if (link.getUsedAt() != null || link.getRevokedAt() != null || !link.getExpiresAt().isAfter(now))
            throw unauthorized();
        link.setUsedAt(now);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String sessionToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        WebSessionEntity session = new WebSessionEntity();
        session.setTokenHash(MagicLinkService.hash(sessionToken));
        session.setUserId(link.getUserId());
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(sessionExpiry));
        sessions.save(session);
        return new SessionGrant(sessionToken, session.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public AppUserEntity authenticate(String token) {
        if (token == null || token.isBlank()) throw unauthorized();
        WebSessionEntity session = sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                MagicLinkService.hash(token), clock.instant()).orElseThrow(WebAuthenticationService::unauthorized);
        return users.findById(session.getUserId()).orElseThrow(WebAuthenticationService::unauthorized);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired access");
    }

    public record SessionGrant(String token, Instant expiresAt) { }
}
