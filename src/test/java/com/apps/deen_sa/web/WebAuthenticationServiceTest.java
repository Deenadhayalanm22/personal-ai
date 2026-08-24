package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.*;
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
}
