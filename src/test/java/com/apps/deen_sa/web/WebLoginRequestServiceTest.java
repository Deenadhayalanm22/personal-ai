package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.MagicLinkService;
import com.apps.deen_sa.conversation.UserFeatureFlagService;
import com.apps.deen_sa.conversation.WhatsAppReplySender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.*;

class WebLoginRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");
    private final UserFeatureFlagService flags = mock(UserFeatureFlagService.class);
    private final MagicLinkService links = mock(MagicLinkService.class);
    private final WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
    private final WebLoginRequestService service = new WebLoginRequestService(
            flags, links, replies, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sendsAOneTimeLinkToAnEnabledNormalizedWhatsAppNumber() {
        when(flags.hasAnyEnabledFeature("WHATSAPP", "919876543210")).thenReturn(true);
        when(links.generateForWhatsAppUser("919876543210")).thenReturn("https://example.com/access?token=secret");

        service.request("+91 98765 43210", "127.0.0.1");

        verify(replies).sendPortalLink("919876543210",
                "Use this secure link to sign in. It can be used once and expires shortly.",
                "https://example.com/access?token=secret");
    }

    @Test
    void doesNotRevealOrCreateLinksForUnknownNumbers() {
        service.request("+91 98765 43210", "127.0.0.1");

        verify(flags).hasAnyEnabledFeature("WHATSAPP", "919876543210");
        verifyNoInteractions(links, replies);
    }

    @Test
    void limitsAUserToThreeLoginMessagesPerWindow() {
        when(flags.hasAnyEnabledFeature("WHATSAPP", "919876543210")).thenReturn(true);
        when(links.generateForWhatsAppUser("919876543210")).thenReturn("https://example.com/access?token=secret");

        for (int request = 0; request < 4; request++)
            service.request("919876543210", "127.0.0.1");

        verify(replies, times(3)).sendPortalLink(anyString(), anyString(), anyString());
    }
}
