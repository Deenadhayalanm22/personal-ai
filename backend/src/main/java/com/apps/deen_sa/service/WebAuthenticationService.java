package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.MagicLinkEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.MagicLinkRepository;
import com.apps.deen_sa.entity.WebSessionEntity;
import com.apps.deen_sa.repository.WebSessionRepository;
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
            AppUserRepository users, @Value("${app.web.session-expiry:10d}") String sessionExpiry) {
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
        WebSessionEntity session = activeSession(token);
        Long activeUserId = session.getActiveUserId() == null ? session.getUserId() : session.getActiveUserId();
        return users.findById(activeUserId).orElseThrow(WebAuthenticationService::unauthorized);
    }

    /**
     * Selects an isolated, non-WhatsApp profile for the current web session.  The
     * primary account remains the session owner, so turning demo mode off can never
     * depend on a client-supplied user id.
     */
    @Transactional
    public DemoProfile setDemoMode(String token, boolean enabled) {
        WebSessionEntity session = activeSession(token);
        if (!enabled) {
            session.setActiveUserId(null);
            return new DemoProfile(false);
        }

        AppUserEntity owner = users.findById(session.getUserId()).orElseThrow(WebAuthenticationService::unauthorized);
        String demoExternalId = "web-demo:" + owner.getId();
        AppUserEntity demo = users.findByChannelAndExternalUserId("WEB_DEMO", demoExternalId)
                .orElseGet(() -> createDemoUser(demoExternalId, owner));
        session.setActiveUserId(demo.getId());
        return new DemoProfile(true);
    }

    @Transactional(readOnly = true)
    public DemoProfile demoProfile(String token) {
        return new DemoProfile(activeSession(token).getActiveUserId() != null);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                        MagicLinkService.hash(token), clock.instant())
                .ifPresent(session -> session.setRevokedAt(clock.instant()));
    }

    private WebSessionEntity activeSession(String token) {
        if (token == null || token.isBlank()) throw unauthorized();
        return sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                MagicLinkService.hash(token), clock.instant()).orElseThrow(WebAuthenticationService::unauthorized);
    }

    private AppUserEntity createDemoUser(String externalId, AppUserEntity owner) {
        AppUserEntity demo = new AppUserEntity();
        demo.setChannel("WEB_DEMO");
        demo.setExternalUserId(externalId);
        demo.setCurrency(owner.getCurrency());
        demo.setLocale(owner.getLocale());
        demo.setTimezone(owner.getTimezone());
        return users.saveAndFlush(demo);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired access");
    }

    public record SessionGrant(String token, Instant expiresAt) { }
    public record DemoProfile(boolean demoMode) { }
}
