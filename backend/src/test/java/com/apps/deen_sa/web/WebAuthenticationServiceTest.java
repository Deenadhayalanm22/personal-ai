package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.MagicLinkEntity;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.WebSessionEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.MagicLinkRepository;
import com.apps.deen_sa.repository.WebSessionRepository;
import com.apps.deen_sa.service.MagicLinkService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebAuthenticationServiceTest {
    private final MagicLinkRepository magicLinks = mock(MagicLinkRepository.class);
    private final WebSessionRepository sessions = mock(WebSessionRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final Instant now = Instant.parse("2026-08-24T10:00:00Z");
    private final WebAuthenticationService service = new WebAuthenticationService(magicLinks, sessions, users,
            Duration.ofHours(12), new SecureRandom(), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void exchangesAValidOneTimeLinkForASession() {
        MagicLinkEntity link = new MagicLinkEntity();
        link.setUserId(42L); link.setExpiresAt(now.plusSeconds(60));
        when(magicLinks.findByTokenHash(MagicLinkService.hash("magic"))).thenReturn(Optional.of(link));

        var grant = service.exchange("magic");

        assertThat(link.getUsedAt()).isEqualTo(now);
        assertThat(grant.expiresAt()).isEqualTo(now.plus(Duration.ofHours(12)));
        ArgumentCaptor<WebSessionEntity> saved = ArgumentCaptor.forClass(WebSessionEntity.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(MagicLinkService.hash(grant.token()));
    }

    @Test
    void rejectsAnAlreadyUsedLink() {
        MagicLinkEntity link = new MagicLinkEntity();
        link.setUserId(42L); link.setExpiresAt(now.plusSeconds(60)); link.setUsedAt(now.minusSeconds(1));
        when(magicLinks.findByTokenHash(MagicLinkService.hash("magic"))).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.exchange("magic"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        verifyNoInteractions(sessions);
    }

    @Test
    void revokesTheCurrentDeviceSessionOnLogout() {
        WebSessionEntity session = new WebSessionEntity();
        when(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                MagicLinkService.hash("session"), now)).thenReturn(Optional.of(session));

        service.logout("session");

        assertThat(session.getRevokedAt()).isEqualTo(now);
    }

    @Test
    void allowsOnlySuperAdminsToUseDemoMode() {
        WebSessionEntity session = activeSession();
        AppUserEntity owner = owner("USER");
        when(users.findById(42L)).thenReturn(Optional.of(owner));

        assertThat(service.demoProfile("session")).isEqualTo(new WebAuthenticationService.DemoProfile(false, false));
        assertThatThrownBy(() -> service.setDemoMode("session", true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        assertThatThrownBy(() -> service.setDemoMode("session", false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        verify(users, never()).saveAndFlush(any());
        assertThat(session.getActiveUserId()).isNull();

        owner.setRole("SUPER_ADMIN");
        AppUserEntity demo = new AppUserEntity(); demo.setId(99L);
        when(users.saveAndFlush(any(AppUserEntity.class))).thenReturn(demo);
        assertThat(service.demoProfile("session")).isEqualTo(new WebAuthenticationService.DemoProfile(false, true));
        assertThat(service.setDemoMode("session", true)).isEqualTo(new WebAuthenticationService.DemoProfile(true, true));
        assertThat(session.getActiveUserId()).isNotNull();
        assertThat(service.setDemoMode("session", false)).isEqualTo(new WebAuthenticationService.DemoProfile(false, true));
    }

    @Test
    void ignoresAnExistingDemoProfileWhenOwnerLosesSuperAdminAccess() {
        WebSessionEntity session = activeSession();
        session.setActiveUserId(99L);
        AppUserEntity owner = owner("USER");
        when(users.findById(42L)).thenReturn(Optional.of(owner));

        assertThat(service.demoProfile("session")).isEqualTo(new WebAuthenticationService.DemoProfile(false, false));
        assertThat(service.authenticate("session")).isSameAs(owner);
        verify(users, never()).findById(99L);
    }

    private WebSessionEntity activeSession() {
        WebSessionEntity session = new WebSessionEntity();
        session.setUserId(42L);
        when(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                MagicLinkService.hash("session"), now)).thenReturn(Optional.of(session));
        return session;
    }

    private AppUserEntity owner(String role) {
        AppUserEntity owner = new AppUserEntity();
        owner.setId(42L);
        owner.setRole(role);
        owner.setPortalEnabled(true);
        return owner;
    }
}
