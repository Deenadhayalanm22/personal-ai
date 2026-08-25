package com.apps.deen_sa.conversation;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MagicLinkServiceTest {
    private final AppUserService users = mock(AppUserService.class);
    private final MagicLinkRepository links = mock(MagicLinkRepository.class);
    private final Instant now = Instant.parse("2026-08-24T10:00:00Z");
    private final MagicLinkService service = new MagicLinkService(
            users, links, "https://money.example.com/", Duration.ofMinutes(15),
            new SecureRandom(), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void generatesOpaqueLinkAndStoresOnlyItsHashAgainstTheUser() {
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        when(users.resolve("WHATSAPP", "919876543210")).thenReturn(user);

        String url = service.generateForWhatsAppUser("+91 98765 43210");

        assertThat(url).startsWith("https://money.example.com/access?token=");
        assertThat(url).doesNotContain("919876543210").doesNotContain("42");
        String token = url.substring(url.indexOf("token=") + 6);
        assertThat(token).hasSize(43);

        ArgumentCaptor<MagicLinkEntity> saved = ArgumentCaptor.forClass(MagicLinkEntity.class);
        verify(links).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(MagicLinkService.hash(token));
        assertThat(saved.getValue().getTokenHash()).doesNotContain(token);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(now);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }

    @Test
    void springUsesTheConfiguredRuntimeConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "app.web.base-url", "https://money.example.com",
                    "app.web.magic-link-expiry", "15m")));
            context.registerBean(AppUserService.class, () -> users);
            context.registerBean(MagicLinkRepository.class, () -> links);
            context.register(MagicLinkService.class);

            context.refresh();

            assertThat(context.getBean(MagicLinkService.class)).isNotNull();
        }
    }

    @Test
    void exposesTheGeneralPortalUrlWithoutAUserToken() {
        assertThat(service.portalUrl()).isEqualTo("https://money.example.com/portal");
        verifyNoInteractions(users, links);
    }
}
